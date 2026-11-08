package com.unisphere.demo;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.auth.PasswordPolicy;
import com.unisphere.common.config.UniSphereProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with a believable university, for demos and manual testing: three finished terms with
 * final results and paid fees, and a term in progress with work left to do. Dates are relative to today, so the
 * dashboards always look current, and randomness has a fixed seed, so every demo database looks the same.
 *
 * <p>It only exists under the {@code demo} profile, never touches a database that already has academic data, and
 * has no default password. It writes with SQL rather than through the services because those rightly refuse to
 * create history (a registration in a finished term, a payment months ago); {@code DemoDataSeederIT} checks that
 * the result is a state the services themselves accept.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String REGISTRAR_EMAIL = "registrar@demo.unisphere.test";
    static final String FINANCE_EMAIL = "finance@demo.unisphere.test";
    static final String LECTURER_EMAIL = "lecturer@demo.unisphere.test";
    static final String STUDENT_EMAIL = "student@demo.unisphere.test";

    /** What the signed-in demo student owes, so their dashboard shows a balance to pay. */
    static final BigDecimal STUDENT_BALANCE = new BigDecimal("12500.00");

    private static final String EMAIL_DOMAIN = "@demo.unisphere.test";
    private static final int PAST_TERMS = 3;
    /** Exams and sign-offs happen during the working day, not at midnight. */
    private static final Duration EXAM_TIME = Duration.ofHours(7);

    private record CourseDef(String code, String title, int credits, int level, String department, String lecturer, String... prerequisites) {
    }

    private record ProgramDef(String code, String name, String department, BigDecimal fee, List<List<String>> plan) {
    }

    private record LecturerDef(String key, String email, String first, String last, String title, String department, String office) {
    }

    private record Term(long id, String name, Instant start, Instant end, Map<String, Long> offerings) {
    }

    private record StudentRow(long id, long userId, int index, ProgramDef program, long accountId) {
    }

    private static final List<CourseDef> COURSES = List.of(
            new CourseDef("CS101", "Introduction to Programming", 3, 100, "CS", "grace"),
            new CourseDef("CS102", "Discrete Structures", 3, 100, "CS", "peter"),
            new CourseDef("CS201", "Data Structures", 4, 200, "CS", "grace", "CS101"),
            new CourseDef("CS202", "Databases", 3, 200, "CS", "peter", "CS101"),
            new CourseDef("CS301", "Operating Systems", 4, 300, "CS", "grace", "CS201"),
            new CourseDef("CS302", "Software Engineering", 3, 300, "CS", "grace", "CS201"),
            new CourseDef("MAT101", "Calculus I", 3, 100, "MAT", "fatuma"),
            new CourseDef("MAT102", "Linear Algebra", 3, 100, "MAT", "fatuma"),
            new CourseDef("MAT201", "Calculus II", 3, 200, "MAT", "fatuma", "MAT101"),
            new CourseDef("MAT301", "Real Analysis", 4, 300, "MAT", "fatuma", "MAT201"),
            new CourseDef("BUS101", "Principles of Management", 3, 100, "BUS", "daniel"),
            new CourseDef("BUS102", "Financial Accounting", 3, 100, "BUS", "daniel"),
            new CourseDef("BUS201", "Marketing", 3, 200, "BUS", "daniel", "BUS101"),
            new CourseDef("BUS301", "Corporate Finance", 3, 300, "BUS", "daniel", "BUS102"),
            new CourseDef("BUS302", "Strategic Management", 3, 300, "BUS", "daniel", "BUS201"));

    /** Each plan lists the courses of the three finished terms (oldest first), then the current one. */
    private static final List<ProgramDef> PROGRAMS = List.of(
            new ProgramDef("BSC-CS", "BSc Computer Science", "CS", new BigDecimal("85000.00"), List.of(
                    List.of("CS101", "CS102", "MAT101"), List.of("CS201", "MAT102", "BUS101"), List.of("CS202", "MAT201"),
                    List.of("CS301", "CS302", "BUS102"))),
            new ProgramDef("BSC-MATH", "BSc Mathematics", "MAT", new BigDecimal("70000.00"), List.of(
                    List.of("MAT101", "MAT102", "CS101"), List.of("MAT201", "CS102", "BUS101"), List.of("CS201", "BUS102"),
                    List.of("MAT301", "CS202", "BUS201"))),
            new ProgramDef("BCOM", "Bachelor of Commerce", "BUS", new BigDecimal("65000.00"), List.of(
                    List.of("BUS101", "BUS102", "MAT101"), List.of("BUS201", "MAT102", "CS101"), List.of("BUS301", "CS102"),
                    List.of("BUS302", "CS202", "MAT201"))));

    /** Offered this term but in nobody's plan, so the catalogue has courses to add. */
    private static final List<String> EXTRA_CURRENT_COURSES = List.of("CS102", "MAT102", "BUS101");

    /** Results of this offering are already with the registrar, ready to finalize. Only approved students take it. */
    private static final String AWAITING_REGISTRAR = "BUS201";
    /** Complete marks the lecturer can still submit. */
    private static final String READY_TO_SUBMIT = "CS302";
    /** Marks half entered. */
    private static final String MARKING_IN_PROGRESS = "CS301";

    private static final List<LecturerDef> LECTURERS = List.of(
            new LecturerDef("grace", LECTURER_EMAIL, "Grace", "Wanjiru", "Dr.", "CS", "Block A, 204"),
            new LecturerDef("peter", "peter.kamau" + EMAIL_DOMAIN, "Peter", "Kamau", "Mr.", "CS", "Block A, 207"),
            new LecturerDef("fatuma", "fatuma.hassan" + EMAIL_DOMAIN, "Fatuma", "Hassan", "Prof.", "MAT", "Block B, 110"),
            new LecturerDef("daniel", "daniel.ochieng" + EMAIL_DOMAIN, "Daniel", "Ochieng", "Dr.", "BUS", "Block C, 301"));

    private static final String[][] STUDENT_NAMES = {
        {"Amina", "Otieno"}, {"Brian", "Mwangi"}, {"Cynthia", "Achieng"}, {"David", "Kiprono"}, {"Esther", "Njeri"},
        {"Felix", "Mutua"}, {"Gloria", "Wambui"}, {"Hassan", "Abdi"}, {"Irene", "Chebet"}, {"James", "Odhiambo"},
        {"Karen", "Muthoni"}, {"Lewis", "Kimani"}, {"Mercy", "Atieno"}, {"Nathan", "Barasa"}, {"Olive", "Wanjala"},
        {"Paul", "Ndegwa"}, {"Queen", "Akinyi"}, {"Robert", "Korir"}, {"Sarah", "Nyambura"}, {"Timothy", "Omondi"},
        {"Umi", "Salim"}, {"Victor", "Maina"}, {"Winnie", "Jeptoo"}, {"Yusuf", "Farah"},
    };
    /** Has not registered at all: the reminder job has someone to remind. */
    private static final int STUDENT_WITHOUT_REGISTRATION = STUDENT_NAMES.length - 2;
    private static final int SUSPENDED_STUDENT = STUDENT_NAMES.length - 1;

    private static final String[] METHODS = {"MOBILE_MONEY", "BANK_TRANSFER", "CASH", "MOBILE_MONEY"};

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final UniSphereProperties properties;
    private final Clock clock;
    private final String password;

    public DemoDataSeeder(JdbcClient jdbc, PasswordEncoder passwordEncoder, AuditService audit, UniSphereProperties properties, Clock clock,
                          @Value("${DEMO_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("The demo profile needs DEMO_PASSWORD: the password of every demo account. There is no default.");
        }
        if (hasAcademicData()) {
            log.info("Demo data not loaded: this database already has academic data.");
            return;
        }
        new Seeding().run();
        log.info("Demo data loaded. Sign in as {}, {}, {} or {} with DEMO_PASSWORD.", STUDENT_EMAIL, LECTURER_EMAIL, REGISTRAR_EMAIL, FINANCE_EMAIL);
    }

    private boolean hasAcademicData() {
        return jdbc.sql("select exists (select 1 from students) or exists (select 1 from semesters) or exists (select 1 from courses)")
                .query(Boolean.class).single();
    }

    /** One run of the seeder, holding the ids it has created so far. */
    private final class Seeding {

        private final Instant now = clock.instant();
        private final LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        private final String currency = properties.finance().currency();
        private final String passwordHash = passwordEncoder.encode(password);
        private final Random random = new Random(20261105);
        private final Map<String, Long> departments = new HashMap<>();
        private final Map<String, Long> programs = new HashMap<>();
        private final Map<String, Long> courses = new HashMap<>();
        private final Map<String, Long> lecturers = new HashMap<>();
        private final Map<String, Long> lecturerUsers = new HashMap<>();
        private final List<StudentRow> students = new ArrayList<>();
        private final List<Band> bands = new ArrayList<>();
        private long registrar;
        private long finance;
        private int nextReference = 1;

        private record Band(BigDecimal min, String letter, BigDecimal points, boolean passing) {
        }

        void run() {
            jdbc.sql("select letter, min_mark, grade_points, passing from grade_bands b join grading_scales s on s.id = b.scale_id "
                            + "where s.is_active order by min_mark desc")
                    .query(rs -> {
                        bands.add(new Band(rs.getBigDecimal("min_mark"), rs.getString("letter"), rs.getBigDecimal("grade_points"), rs.getBoolean("passing")));
                    });
            registrar = user(REGISTRAR_EMAIL, "Rose", "Mutiso", "ADMIN");
            finance = user(FINANCE_EMAIL, "Faith", "Karimi", "FINANCE");
            academicStructure();
            people();

            List<Term> terms = new ArrayList<>();
            for (int i = PAST_TERMS; i >= 1; i--) {
                terms.add(pastTerm(i));
            }
            Term current = currentTerm();
            for (int t = 0; t < PAST_TERMS; t++) {
                finishedTermActivity(terms.get(t), t);
            }
            currentTermActivity(current);
            announcements(current);
            refreshEnrolment();
            audit.recordAs(null, AuditAction.CREATED, "DemoData", null, Map.of("students", students.size(), "terms", terms.size() + 1));
        }

        // ---- Structure and people ----

        private void academicStructure() {
            departments.put("CS", insertId("insert into departments (code, name, faculty) values ('CS', 'Computer Science', 'Science and Technology') returning id", Map.of()));
            departments.put("MAT", insertId("insert into departments (code, name, faculty) values ('MAT', 'Mathematics', 'Science and Technology') returning id", Map.of()));
            departments.put("BUS", insertId("insert into departments (code, name, faculty) values ('BUS', 'Business', 'Business and Economics') returning id", Map.of()));
            for (ProgramDef p : PROGRAMS) {
                programs.put(p.code(), insertId("""
                        insert into programs (department_id, code, name, award_level, duration_years)
                        values (:department, :code, :name, 'BACHELOR', 4) returning id""",
                        Map.of("department", departments.get(p.department()), "code", p.code(), "name", p.name())));
            }
            for (CourseDef c : COURSES) {
                courses.put(c.code(), insertId("""
                        insert into courses (department_id, code, title, credits, level)
                        values (:department, :code, :title, :credits, :level) returning id""",
                        Map.of("department", departments.get(c.department()), "code", c.code(), "title", c.title(),
                                "credits", c.credits(), "level", c.level())));
            }
            for (CourseDef c : COURSES) {
                for (String prerequisite : c.prerequisites()) {
                    jdbc.sql("insert into course_prerequisites (course_id, prerequisite_id) values (:course, :prerequisite)")
                            .param("course", courses.get(c.code())).param("prerequisite", courses.get(prerequisite)).update();
                }
            }
        }

        private void people() {
            for (LecturerDef l : LECTURERS) {
                long userId = user(l.email(), l.first(), l.last(), "LECTURER");
                lecturerUsers.put(l.key(), userId);
                lecturers.put(l.key(), insertId("""
                        insert into lecturers (user_id, staff_number, department_id, title, office)
                        values (:user, 'STF-' || lpad(nextval('staff_number_seq')::text, 5, '0'), :department, :title, :office) returning id""",
                        Map.of("user", userId, "department", departments.get(l.department()), "title", l.title(), "office", l.office())));
            }
            LocalDate admitted = today.minusDays(14L + 134L * PAST_TERMS + 120L);
            for (int i = 0; i < STUDENT_NAMES.length; i++) {
                String[] name = STUDENT_NAMES[i];
                String email = i == 0 ? STUDENT_EMAIL : (name[0] + "." + name[1]).toLowerCase(Locale.ROOT) + EMAIL_DOMAIN;
                long userId = user(email, name[0], name[1], "STUDENT");
                ProgramDef program = PROGRAMS.get(i % PROGRAMS.size());
                long studentId = insertId("""
                        insert into students (user_id, student_number, program_id, year_of_study, admission_date, status)
                        values (:user, 'S' || :year || '-' || lpad(nextval('student_number_seq')::text, 5, '0'), :program, 2, :admitted, :status)
                        returning id""",
                        Map.of("user", userId, "year", admitted.getYear(), "program", programs.get(program.code()), "admitted", admitted,
                                "status", i == SUSPENDED_STUDENT ? "SUSPENDED" : "ACTIVE"));
                long accountId = insertId("insert into fee_accounts (student_id, currency) values (:student, :currency) returning id",
                        Map.of("student", studentId, "currency", currency));
                students.add(new StudentRow(studentId, userId, i, program, accountId));
            }
        }

        private long user(String email, String first, String last, String role) {
            PasswordPolicy.validate(password, email);
            long id = insertId("""
                    insert into users (email, password_hash, first_name, last_name, status)
                    values (:email, :hash, :first, :last, 'ACTIVE') returning id""",
                    Map.of("email", email, "hash", passwordHash, "first", first, "last", last));
            jdbc.sql("insert into user_roles (user_id, role_id) values (:user, (select id from roles where name = :role))")
                    .param("user", id).param("role", role).update();
            return id;
        }

        // ---- Terms ----

        /** @param termsAgo 1 is the term just before the current one */
        private Term pastTerm(int termsAgo) {
            LocalDate end = today.minusDays(28L + 134L * (termsAgo - 1));
            LocalDate start = end.minusDays(120);
            Instant opens = midnight(start.minusDays(21));
            long id = term(start, end, opens, opens.plus(Duration.ofDays(14)), midnight(start.plusDays(14)), false, false);
            int planIndex = PAST_TERMS - termsAgo;
            Set<String> codes = new LinkedHashSet<>();
            PROGRAMS.forEach(p -> codes.addAll(p.plan().get(planIndex)));
            return new Term(id, termName(start), midnight(start), midnight(end), offerings(id, codes, midnight(end).minus(Duration.ofDays(7)).plus(EXAM_TIME)));
        }

        private Term currentTerm() {
            LocalDate start = today.minusDays(14);
            LocalDate end = today.plusDays(106);
            long id = term(start, end, now.minus(Duration.ofDays(28)), now.plus(Duration.ofDays(5)), now.plus(Duration.ofDays(12)), true, true);
            Set<String> codes = new LinkedHashSet<>();
            PROGRAMS.forEach(p -> codes.addAll(p.plan().get(PAST_TERMS)));
            codes.addAll(EXTRA_CURRENT_COURSES);
            return new Term(id, termName(start), midnight(start), midnight(end), offerings(id, codes, midnight(end).minus(Duration.ofDays(14)).plus(EXAM_TIME)));
        }

        private long term(LocalDate start, LocalDate end, Instant opens, Instant closes, Instant addDrop, boolean open, boolean current) {
            int term = (start.getMonthValue() - 1) / 4 + 1;
            String academicYear = start.getYear() + "/" + (start.getYear() + 1);
            Map<String, Object> params = new HashMap<>();
            params.put("code", start.getYear() + "-T" + term);
            params.put("name", termName(start));
            params.put("year", academicYear);
            params.put("term", term);
            params.put("start", start);
            params.put("end", end);
            params.put("opens", utc(opens));
            params.put("closes", utc(closes));
            params.put("addDrop", utc(addDrop));
            params.put("open", open);
            params.put("current", current);
            return insertId("""
                    insert into semesters (code, name, academic_year, term, start_date, end_date, registration_opens_at, registration_closes_at,
                                           add_drop_deadline, registration_open, requires_approval, min_credits, max_credits, is_current)
                    values (:code, :name, :year, :term, :start, :end, :opens, :closes, :addDrop, :open, true, 6, 24, :current)
                    returning id""", params);
        }

        private String termName(LocalDate start) {
            return "Term %d, %d/%d".formatted((start.getMonthValue() - 1) / 4 + 1, start.getYear(), start.getYear() + 1);
        }

        /** Every offering of a term gets its own weekly slot and room, so nothing can clash. */
        private Map<String, Long> offerings(long termId, Set<String> codes, Instant firstExam) {
            Map<String, Long> ids = new LinkedHashMap<>();
            int slot = 0;
            for (String code : codes) {
                CourseDef course = COURSES.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
                long id = insertId("""
                        insert into course_offerings (course_id, semester_id, section, lecturer_id, capacity, exam_starts_at, exam_venue)
                        values (:course, :term, 'A', :lecturer, 60, :exam, :venue) returning id""",
                        Map.of("course", courses.get(code), "term", termId, "lecturer", lecturers.get(course.lecturer()),
                                "exam", utc(firstExam.plus(Duration.ofDays(slot))), "venue", "Exam Hall " + (slot % 3 + 1)));
                jdbc.sql("""
                                insert into offering_sessions (offering_id, day_of_week, start_time, end_time, venue)
                                values (:offering, :day, :start, :end, :venue)""")
                        .param("offering", id)
                        .param("day", slot % 5 + 1)
                        .param("start", LocalTime.of(8 + slot / 5 * 3, 0))
                        .param("end", LocalTime.of(10 + slot / 5 * 3, 0))
                        .param("venue", "LT" + (slot % 5 + 1))
                        .update();
                ids.put(code, id);
                slot++;
            }
            return ids;
        }

        // ---- Finished terms: approved, marked, finalized, billed and paid ----

        private void finishedTermActivity(Term term, int planIndex) {
            Instant finalized = term.end().plus(Duration.ofDays(10)).plus(EXAM_TIME);
            Map<String, Long> structures = feeStructures(term);
            for (StudentRow s : students) {
                Instant submitted = term.start().minus(Duration.ofDays(10L + s.index() % 7));
                long registration = registration(s, term, "APPROVED", submitted, null, s.program().plan().get(planIndex), true).id;
                Map<String, Long> items = registrationItems(registration);
                for (Map.Entry<String, Long> item : items.entrySet()) {
                    int total = mark(s, item.getKey(), planIndex);
                    result(item.getValue(), total, lecturerUserOf(item.getKey()), finalized);
                }
                BigDecimal fee = s.program().fee();
                long structure = structures.get(s.program().code());
                charge(s, term, structure, "Tuition — " + term.name(), fee, term.start().minus(Duration.ofDays(3)));
                boolean recent = planIndex == PAST_TERMS - 1;
                BigDecimal paid = recent && s.index() == 7 ? fee.subtract(new BigDecimal("5000.00")) : fee;
                // The last finished term's payments fall inside the dashboard's 12-week chart.
                Instant paidAt = recent ? now.minus(Duration.ofDays(35L + random.nextInt(45))) : term.start().plus(Duration.ofDays(random.nextInt(60))).plus(EXAM_TIME);
                payment(s, paid, paidAt, "COMPLETED", null);
            }
            term.offerings().forEach((code, offering) -> {
                jdbc.sql("""
                                update course_offerings set results_status = 'FINALIZED', results_submitted_at = :submitted,
                                       results_finalized_at = :finalized, results_finalized_by = :registrar where id = :id""")
                        .param("submitted", utc(finalized.minus(Duration.ofDays(3))))
                        .param("finalized", utc(finalized.plus(Duration.ofMinutes(offering))))
                        .param("registrar", registrar)
                        .param("id", offering)
                        .update();
            });
            if (planIndex == PAST_TERMS - 1) {
                for (StudentRow s : students) {
                    for (String code : s.program().plan().get(planIndex)) {
                        notification(s.userId(), "RESULTS_RELEASED", "Results released: " + code,
                                "Your final result for %s (%s) is now available.".formatted(code, term.name()), "/student/results",
                                finalized, finalized.plus(Duration.ofDays(2)));
                    }
                }
            }
        }

        /** Each student has a typical level and improves slowly; the signed-in demo student has a clear upward trend. */
        private int mark(StudentRow s, String course, int planIndex) {
            if (course.equals("MAT102") && s.index() % 7 == 3) {
                return 35; // A failed course nothing depends on, so the record shows a fail without breaking prerequisites.
            }
            int level = s.index() == 0 ? 60 + planIndex * 6 : 48 + (s.index() * 37) % 34 + planIndex * 2;
            return Math.clamp(level + random.nextInt(11) - 5L, 42, 95);
        }

        private void result(long item, int total, long enteredBy, Instant finalizedAt) {
            int cat = Math.round(total * 0.3f);
            Map<String, Object> params = new HashMap<>();
            params.put("item", item);
            params.put("cat", BigDecimal.valueOf(cat));
            params.put("exam", BigDecimal.valueOf((long) total - cat));
            params.put("total", BigDecimal.valueOf(total));
            params.put("enteredBy", enteredBy);
            Band band = finalizedAt == null ? null : bands.stream().filter(b -> BigDecimal.valueOf(total).compareTo(b.min()) >= 0).findFirst().orElseThrow();
            params.put("grade", band == null ? null : band.letter());
            params.put("points", band == null ? null : band.points());
            params.put("passed", band == null ? null : band.passing());
            params.put("finalized", finalizedAt == null ? null : utc(finalizedAt));
            jdbc.sql("""
                            insert into results (registration_item_id, cat_mark, exam_mark, total_mark, grade, grade_points, passed, finalized_at, entered_by)
                            values (:item, :cat, :exam, :total, cast(:grade as varchar), cast(:points as numeric), cast(:passed as boolean),
                                    cast(:finalized as timestamptz), :enteredBy)""")
                    .params(params)
                    .update();
        }

        // ---- The current term: everything still in motion ----

        private void currentTermActivity(Term term) {
            Map<String, Long> structures = feeStructures(term);
            for (StudentRow s : students) {
                notification(s.userId(), "REGISTRATION_OPENED", "Registration is open for " + term.name(),
                        "Course registration is open. Add your courses and submit before it closes.", "/student/registration",
                        now.minus(Duration.ofDays(28)), s.index() % 2 == 0 ? now.minus(Duration.ofDays(27)) : null);
                if (s.index() == STUDENT_WITHOUT_REGISTRATION || s.index() == SUSPENDED_STUDENT) {
                    continue;
                }
                List<String> plan = s.program().plan().get(PAST_TERMS);
                String status = currentStatus(s);
                // Squares modulo a prime repeat, so several students submit on the same day and the activity chart has a shape.
                Instant submitted = now.minus(Duration.ofDays(4L + ((long) s.index() * s.index()) % 17));
                switch (status) {
                    case "APPROVED" -> {
                        registration(s, term, status, submitted, null, plan, true);
                        charge(s, term, structures.get(s.program().code()), "Tuition — " + term.name(), s.program().fee(), submitted.plus(Duration.ofDays(1)));
                        currentPayments(s, submitted);
                        notification(s.userId(), "REGISTRATION_APPROVED", "Registration confirmed",
                                "Your registration for %s is confirmed. Your timetable is ready.".formatted(term.name()), "/student/timetable",
                                submitted.plus(Duration.ofHours(6)), null);
                    }
                    case "SUBMITTED" -> {
                        registration(s, term, status, submitted, null, plan, true);
                        notification(s.userId(), "REGISTRATION_SUBMITTED", "Registration submitted",
                                "Your registration for %s has been submitted and is waiting for approval.".formatted(term.name()), "/student/registration",
                                submitted, null);
                    }
                    case "REJECTED" -> {
                        String reason = "Please add another course: your load is below what your program expects";
                        registration(s, term, status, submitted, reason, plan.subList(0, 1), false);
                        notification(s.userId(), "REGISTRATION_REJECTED", "Registration not approved",
                                "Your registration for %s was not approved: %s. You can change your courses and submit again.".formatted(term.name(), reason),
                                "/student/registration", submitted.plus(Duration.ofHours(20)), null);
                    }
                    default -> registration(s, term, "DRAFT", null, null, plan.subList(0, 2), false);
                }
            }
            currentMarks(term);
            jdbc.sql("""
                            insert into notifications (user_id, type, title, message, link, created_at)
                            select s.user_id, 'FEE_REMINDER', 'Fee balance reminder', 'You have an outstanding fee balance. You can pay online from the Fees page.',
                                   '/student/fees', :at
                            from students s where s.id = :student""")
                    .param("at", utc(now.minus(Duration.ofDays(2))))
                    .param("student", students.getFirst().id())
                    .update();
        }

        /** Mathematics students take the course whose results are with the registrar, and are all approved. */
        private String currentStatus(StudentRow s) {
            if (s.program().plan().get(PAST_TERMS).contains(AWAITING_REGISTRAR) || s.index() == 0) {
                return "APPROVED";
            }
            return switch (s.index() % 8) {
                case 2 -> "SUBMITTED";
                case 3 -> "DRAFT";
                case 5 -> "SUBMITTED";
                case 6 -> s.index() == 6 ? "REJECTED" : "SUBMITTED";
                default -> "APPROVED";
            };
        }

        private void currentPayments(StudentRow s, Instant submitted) {
            BigDecimal fee = s.program().fee();
            Instant paidAt = submitted.plus(Duration.ofDays(1)).isAfter(now) ? now.minus(Duration.ofHours(2)) : submitted.plus(Duration.ofDays(1));
            if (s.index() == 0) {
                payment(s, fee.subtract(STUDENT_BALANCE), paidAt, "COMPLETED", null);
                return;
            }
            switch (s.index() % 4) {
                case 0 -> payment(s, fee, paidAt, "COMPLETED", null);
                case 1 -> {
                    payment(s, fee.divide(BigDecimal.TWO), paidAt, "COMPLETED", null);
                    if (s.index() == 1) {
                        payment(s, new BigDecimal("10000.00"), paidAt, "REVERSED", "Cheque bounced");
                        payment(s, new BigDecimal("500.13"), paidAt, "FAILED", "The payment was declined by the provider. No money was taken.");
                        jdbc.sql("""
                                        insert into fee_charges (account_id, semester_id, kind, description, amount, status, created_by, created_at,
                                                                 voided_at, voided_by, void_reason)
                                        values (:account, null, 'MANUAL', 'Library fine', 1500.00, 'VOIDED', :finance, :at, :at, :finance, 'Book was returned on time')""")
                                .param("account", s.accountId()).param("finance", finance).param("at", utc(paidAt))
                                .update();
                    }
                }
                case 2 -> jdbc.sql("""
                                insert into fee_charges (account_id, semester_id, kind, description, amount, status, created_by, created_at)
                                values (:account, null, 'MANUAL', 'Library fine', 750.00, 'ACTIVE', :finance, :at)""")
                        .param("account", s.accountId()).param("finance", finance).param("at", utc(paidAt))
                        .update();
                default -> {
                    // A student who overpaid is in credit.
                    payment(s, fee.add(new BigDecimal("2000.00")), paidAt, "COMPLETED", null);
                }
            }
        }

        private void currentMarks(Term term) {
            finishMarks(term, READY_TO_SUBMIT, 1.0);
            finishMarks(term, MARKING_IN_PROGRESS, 0.5);
            finishMarks(term, AWAITING_REGISTRAR, 1.0);
            jdbc.sql("update course_offerings set results_status = 'SUBMITTED', results_submitted_at = :at where id = :id")
                    .param("at", utc(now.minus(Duration.ofDays(1))))
                    .param("id", term.offerings().get(AWAITING_REGISTRAR))
                    .update();
        }

        private void finishMarks(Term term, String code, double share) {
            List<Long> classList = jdbc.sql("""
                            select i.id from registration_items i join registrations r on r.id = i.registration_id
                            where i.offering_id = :offering and i.status = 'ACTIVE' and r.status = 'APPROVED' order by i.id""")
                    .param("offering", term.offerings().get(code))
                    .query(Long.class).list();
            int marked = (int) Math.ceil(classList.size() * share);
            for (long item : classList.subList(0, marked)) {
                result(item, 50 + random.nextInt(40), lecturerUserOf(code), null);
            }
        }

        private void announcements(Term term) {
            announcement(term, READY_TO_SUBMIT, "Project groups are published",
                    "Project groups are on the class page. Meet your group this week and agree on a topic before the next lecture.");
            announcement(term, MARKING_IN_PROGRESS, "Room change for Thursday",
                    "Thursday's lecture moves to LT3 because of the open day. Everything else stays the same.");
            announcement(term, "BUS102", "CAT 1 next week",
                    "The first CAT covers chapters 1 to 4. Bring a calculator and your student ID.");
        }

        private void announcement(Term term, String code, String title, String body) {
            long offering = term.offerings().get(code);
            CourseDef course = COURSES.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
            Instant at = now.minus(Duration.ofDays(1L + random.nextInt(5)));
            jdbc.sql("""
                            insert into announcements (offering_id, author_id, title, body, created_at)
                            values (:offering, :author, :title, :body, :at)""")
                    .param("offering", offering).param("author", lecturerUsers.get(course.lecturer())).param("title", title)
                    .param("body", body).param("at", utc(at))
                    .update();
            jdbc.sql("""
                            insert into notifications (user_id, type, title, message, link, created_at)
                            select distinct s.user_id, 'ANNOUNCEMENT', :title, :body, :link, :at
                            from registration_items i
                            join registrations r on r.id = i.registration_id
                            join students s on s.id = r.student_id
                            where i.offering_id = :offering and i.status = 'ACTIVE' and r.status = 'APPROVED'""")
                    .param("title", code + ": " + title).param("body", body).param("link", "/announcements?offeringId=" + offering)
                    .param("at", utc(at)).param("offering", offering)
                    .update();
        }

        // ---- Rows ----

        private record Registration(long id) {
        }

        private Registration registration(StudentRow s, Term term, String status, Instant submitted, String reason, List<String> codes, boolean seats) {
            boolean reviewed = status.equals("APPROVED") || status.equals("REJECTED");
            Instant added = submitted == null ? now.minus(Duration.ofDays(2)) : submitted.minus(Duration.ofHours(4));
            Map<String, Object> params = new HashMap<>();
            params.put("student", s.id());
            params.put("term", term.id());
            params.put("status", status);
            params.put("submitted", submitted == null ? null : utc(submitted));
            params.put("reviewer", reviewed ? registrar : null);
            params.put("reviewedAt", reviewed
                    ? utc(Objects.requireNonNull(submitted, "A reviewed registration must have been submitted").plus(Duration.ofHours(6)))
                    : null);
            params.put("reason", reason);
            params.put("created", utc(added));
            long id = insertId("""
                    insert into registrations (student_id, semester_id, status, submitted_at, reviewed_by, reviewed_at, rejection_reason, created_at)
                    values (:student, :term, :status, cast(:submitted as timestamptz), cast(:reviewer as bigint), cast(:reviewedAt as timestamptz),
                            cast(:reason as varchar), :created)
                    returning id""", params);
            for (String code : codes) {
                jdbc.sql("""
                                insert into registration_items (registration_id, offering_id, status, seat_reserved, added_at)
                                values (:registration, :offering, 'ACTIVE', :seat, :added)""")
                        .param("registration", id).param("offering", term.offerings().get(code)).param("seat", seats).param("added", utc(added))
                        .update();
            }
            return new Registration(id);
        }

        /** Course code → registration item. */
        private Map<String, Long> registrationItems(long registration) {
            Map<String, Long> items = new LinkedHashMap<>();
            jdbc.sql("""
                            select c.code, i.id from registration_items i
                            join course_offerings o on o.id = i.offering_id
                            join courses c on c.id = o.course_id
                            where i.registration_id = :registration order by i.id""")
                    .param("registration", registration)
                    .query(rs -> {
                        items.put(rs.getString("code"), rs.getLong("id"));
                    });
            return items;
        }

        private Map<String, Long> feeStructures(Term term) {
            Map<String, Long> ids = new HashMap<>();
            for (ProgramDef p : PROGRAMS) {
                ids.put(p.code(), insertId("""
                        insert into fee_structures (program_id, semester_id, amount, description)
                        values (:program, :term, :amount, :description) returning id""",
                        Map.of("program", programs.get(p.code()), "term", term.id(), "amount", p.fee(), "description", "Tuition")));
            }
            return ids;
        }

        private void charge(StudentRow s, Term term, long structure, String description, BigDecimal amount, Instant at) {
            jdbc.sql("""
                            insert into fee_charges (account_id, semester_id, fee_structure_id, kind, description, amount, created_by, created_at)
                            values (:account, :term, :structure, 'TUITION', :description, :amount, :finance, :at)""")
                    .param("account", s.accountId()).param("term", term.id()).param("structure", structure).param("description", description)
                    .param("amount", amount).param("finance", finance).param("at", utc(at))
                    .update();
        }

        /** @param note the reason for a reversed payment, or the provider's message for a failed one */
        private void payment(StudentRow s, BigDecimal amount, Instant paidAt, String status, String note) {
            String method = status.equals("FAILED") ? "CARD" : METHODS[nextReference % METHODS.length];
            boolean online = status.equals("FAILED") || method.equals("MOBILE_MONEY");
            Map<String, Object> params = new HashMap<>();
            params.put("account", s.accountId());
            params.put("amount", amount);
            params.put("method", method);
            params.put("reference", (online ? "UNI-DEMO-" : "RCPT-DEMO-") + "%05d".formatted(nextReference++));
            params.put("channel", online ? "ONLINE" : "OFFICE");
            params.put("provider", online ? "simulated" : null);
            params.put("status", status);
            params.put("failure", status.equals("FAILED") ? note : null);
            params.put("paidAt", utc(paidAt));
            params.put("recordedBy", online ? null : finance);
            params.put("reversedAt", status.equals("REVERSED") ? utc(paidAt.plus(Duration.ofHours(30)).isAfter(now) ? now : paidAt.plus(Duration.ofHours(30))) : null);
            params.put("reversedBy", status.equals("REVERSED") ? finance : null);
            params.put("reversal", status.equals("REVERSED") ? note : null);
            jdbc.sql("""
                            insert into payments (account_id, amount, method, reference, channel, provider, status, failure_reason, paid_at, recorded_by,
                                                  reversed_at, reversed_by, reversal_reason)
                            values (:account, :amount, :method, :reference, :channel, cast(:provider as varchar), :status, cast(:failure as varchar), :paidAt,
                                    cast(:recordedBy as bigint), cast(:reversedAt as timestamptz), cast(:reversedBy as bigint), cast(:reversal as varchar))""")
                    .params(params)
                    .update();
        }

        private void notification(long user, String type, String title, String message, String link, Instant at, Instant readAt) {
            Map<String, Object> params = new HashMap<>();
            params.put("user", user);
            params.put("type", type);
            params.put("title", title);
            params.put("message", message);
            params.put("link", link);
            params.put("at", utc(at.isAfter(now) ? now : at));
            params.put("readAt", readAt == null || readAt.isAfter(now) ? null : utc(readAt));
            jdbc.sql("""
                            insert into notifications (user_id, type, title, message, link, created_at, read_at)
                            values (:user, :type, :title, :message, :link, :at, cast(:readAt as timestamptz))""")
                    .params(params)
                    .update();
        }

        /** Seats follow from who holds one, as they do when the services reserve them. */
        private void refreshEnrolment() {
            jdbc.sql("""
                            update course_offerings o
                            set enrolled_count = (select count(*) from registration_items i where i.offering_id = o.id and i.seat_reserved)""")
                    .update();
        }

        private long lecturerUserOf(String code) {
            return lecturerUsers.get(COURSES.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow().lecturer());
        }

        private long insertId(String sql, Map<String, ?> params) {
            return jdbc.sql(sql).params(params).query(Long.class).single();
        }

        private Instant midnight(LocalDate date) {
            return date.atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        private OffsetDateTime utc(Instant instant) {
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
    }
}
