package com.unisphere.finance;

import com.unisphere.academic.Program;
import com.unisphere.academic.ProgramRepository;
import com.unisphere.academic.ProgramService;
import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.security.CurrentUser;
import com.unisphere.finance.dto.FinanceDtos.ChargePreview;
import com.unisphere.finance.dto.FinanceDtos.FeeStructureRequest;
import com.unisphere.finance.dto.FinanceDtos.FeeStructureRow;
import com.unisphere.finance.dto.FinanceDtos.FeeStructures;
import com.unisphere.finance.dto.FinanceDtos.GenerationResult;
import com.unisphere.finance.dto.FinanceDtos.ProgramRef;
import com.unisphere.finance.dto.FinanceDtos.StructureView;
import com.unisphere.registration.Registration;
import com.unisphere.registration.RegistrationRepository;
import com.unisphere.registration.RegistrationStatus;
import com.unisphere.semester.Semester;
import com.unisphere.semester.SemesterService;
import com.unisphere.semester.dto.SemesterDtos.SemesterRef;
import com.unisphere.student.Student;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fee structures (a flat fee per program and semester) and billing (FIN-2): charges are generated for every
 * student with an approved registration in the semester. Generation is idempotent: running it again bills
 * only students who were not billed yet, and a database constraint guarantees no one is billed twice.
 */
@Service
@RequiredArgsConstructor
public class FeeStructureService {

    private final FeeStructureRepository structures;
    private final FeeChargeRepository charges;
    private final FeeAccountRepository accounts;
    private final RegistrationRepository registrations;
    private final ProgramRepository programRepository;
    private final ProgramService programs;
    private final SemesterService semesters;
    private final AuditService audit;
    private final UniSphereProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FeeStructures list(Long semesterId) {
        Semester semester = semesters.load(semesterId);
        Map<Long, FeeStructure> byProgram = structures.findBySemesterId(semesterId).stream()
                .collect(Collectors.toMap(s -> s.getProgram().getId(), Function.identity()));
        Map<Long, Long> charged = chargeCounts(byProgram.values());
        // Active programs, plus any inactive program that still has a fee set for this semester.
        Map<Long, Program> shown = new HashMap<>();
        programRepository.search(null, true).forEach(p -> shown.put(p.getId(), p));
        byProgram.values().forEach(s -> shown.putIfAbsent(s.getProgram().getId(), s.getProgram()));
        List<FeeStructureRow> rows = shown.values().stream()
                .sorted(Comparator.comparing(Program::getCode))
                .map(p -> {
                    FeeStructure s = byProgram.get(p.getId());
                    return new FeeStructureRow(new ProgramRef(p.getId(), p.getCode(), p.getName()),
                            s == null ? null : new StructureView(s.getId(), s.getAmount(), s.getDescription()),
                            s == null ? 0 : charged.getOrDefault(s.getId(), 0L));
                })
                .toList();
        return new FeeStructures(SemesterRef.from(semester), properties.finance().currency(), rows);
    }

    /** Creates or updates the fee for a program in a semester. A fee that has been billed can no longer change amount. */
    @Transactional
    public FeeStructures save(FeeStructureRequest r) {
        Program program = programs.load(r.programId());
        Semester semester = semesters.load(r.semesterId());
        FeeStructure structure = structures.findByProgramIdAndSemesterId(program.getId(), semester.getId()).orElse(null);
        if (structure == null) {
            structure = structures.save(new FeeStructure(program, semester, r.amount(), r.description()));
        } else {
            long billed = charges.countByFeeStructureId(structure.getId());
            if (billed > 0 && structure.getAmount().compareTo(r.amount()) != 0) {
                throw ApiException.invalidState(("This fee has already been charged to %d student(s), so its amount can no longer change. "
                        + "Add a manual charge or void individual charges instead.").formatted(billed));
            }
            structure.update(r.amount(), r.description());
        }
        audit.record(AuditAction.FEE_STRUCTURE_SAVED, "FeeStructure", structure.getId(),
                Map.of("program", program.getCode(), "semester", semester.getCode(), "amount", r.amount()));
        return list(semester.getId());
    }

    @Transactional
    public void delete(Long structureId) {
        FeeStructure structure = structures.findById(structureId).orElseThrow(() -> ApiException.notFound("Fee structure", structureId));
        long billed = charges.countByFeeStructureId(structureId);
        if (billed > 0) {
            throw ApiException.inUse("This fee has been charged to %d student(s) and cannot be deleted.".formatted(billed));
        }
        String program = structure.getProgram().getCode();
        String semester = structure.getSemester().getCode();
        structures.delete(structure);
        audit.record(AuditAction.FEE_STRUCTURE_DELETED, "FeeStructure", structureId, Map.of("program", program, "semester", semester));
    }

    @Transactional(readOnly = true)
    public ChargePreview preview(Long semesterId) {
        Semester semester = semesters.load(semesterId);
        Plan plan = plan(semester);
        return new ChargePreview(SemesterRef.from(semester), properties.finance().currency(), plan.registered, plan.alreadyCharged,
                plan.toCharge.size(), plan.toCharge.stream().map(Billing::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                plan.withoutFee, List.copyOf(plan.programsWithoutFee));
    }

    @Transactional
    public GenerationResult generate(Long semesterId) {
        Semester semester = semesters.load(semesterId);
        Plan plan = plan(semester);
        Instant now = clock.instant();
        Long userId = CurrentUser.id();
        int charged = 0;
        int alreadyCharged = plan.alreadyCharged;
        BigDecimal total = BigDecimal.ZERO;
        for (Billing b : plan.toCharge) {
            accounts.ensure(b.student().getId(), properties.finance().currency());
            FeeAccount account = accounts.findByStudentId(b.student().getId()).orElseThrow();
            int inserted = charges.insertTuition(account.getId(), semester.getId(), b.structure().getId(),
                    b.structure().getDescription() + " — " + semester.getName(), b.amount(), userId, now);
            if (inserted == 1) {
                charged++;
                total = total.add(b.amount());
            } else {
                // Billed by a concurrent run between planning and inserting.
                alreadyCharged++;
            }
        }
        audit.record(AuditAction.CHARGES_GENERATED, "Semester", semesterId,
                Map.of("semester", semester.getCode(), "charged", charged, "amount", total, "withoutFee", plan.withoutFee));
        return new GenerationResult(charged, alreadyCharged, plan.withoutFee, total);
    }

    private record Billing(Student student, FeeStructure structure) {
        BigDecimal amount() {
            return structure.getAmount();
        }
    }

    private record Plan(int registered, int alreadyCharged, List<Billing> toCharge, int withoutFee, Set<String> programsWithoutFee) {
    }

    private Plan plan(Semester semester) {
        List<Registration> approved = registrations.findBySemesterIdAndStatus(semester.getId(), RegistrationStatus.APPROVED);
        Map<Long, FeeStructure> byProgram = structures.findBySemesterId(semester.getId()).stream()
                .collect(Collectors.toMap(s -> s.getProgram().getId(), Function.identity()));
        Set<Long> billed = byProgram.isEmpty() ? Set.of()
                : new HashSet<>(charges.findStudentIdsChargedFor(byProgram.values().stream().map(FeeStructure::getId).toList()));
        int already = 0;
        int withoutFee = 0;
        Set<String> missing = new TreeSet<>();
        List<Billing> toCharge = new ArrayList<>();
        for (Registration r : approved) {
            Student student = r.getStudent();
            FeeStructure structure = byProgram.get(student.getProgram().getId());
            if (structure == null) {
                withoutFee++;
                missing.add(student.getProgram().getCode());
            } else if (billed.contains(student.getId())) {
                already++;
            } else {
                toCharge.add(new Billing(student, structure));
            }
        }
        return new Plan(approved.size(), already, toCharge, withoutFee, missing);
    }

    private Map<Long, Long> chargeCounts(Collection<FeeStructure> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return charges.countByStructures(list.stream().map(FeeStructure::getId).toList()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
