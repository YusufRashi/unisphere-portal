package com.unisphere.result;

import com.unisphere.audit.AuditAction;
import com.unisphere.audit.AuditService;
import com.unisphere.common.exception.ApiException;
import com.unisphere.common.exception.ErrorCode;
import com.unisphere.result.dto.ResultDtos.BandRequest;
import com.unisphere.result.dto.ResultDtos.BandResponse;
import com.unisphere.result.dto.ResultDtos.GradingScaleResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The active grading scale. Changing it affects grade previews and future finalizations only: finalized
 * results keep the grade they were given.
 */
@Service
@RequiredArgsConstructor
public class GradingScaleService {

    private final GradingScaleRepository scales;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public GradingScaleResponse get() {
        return response(active());
    }

    /** Bands of the active scale, highest first. */
    @Transactional(readOnly = true)
    public List<GradingRules.Band> activeBands() {
        return GradingRules.highestFirst(active().rules());
    }

    @Transactional
    public GradingScaleResponse replace(List<BandRequest> request) {
        List<GradingRules.Band> bands = request.stream().map(BandRequest::toRule).toList();
        List<String> problems = GradingRules.problems(bands);
        if (!problems.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The grading scale is not valid.")
                    .with("errors", problems.stream().map(p -> Map.of("field", "bands", "message", p)).toList());
        }
        GradingScale scale = scales.lockActive().orElseThrow(GradingScaleService::missing);
        String before = describe(scale.rules());
        scale.clearBands();
        // Delete the old rows before inserting the new ones, which may reuse the same letters.
        scales.flush();
        scale.addBands(bands);
        audit.record(AuditAction.GRADING_SCALE_CHANGED, "GradingScale", scale.getId(),
                Map.of("before", before, "after", describe(bands)));
        return response(scale);
    }

    private GradingScale active() {
        return scales.findByActiveTrue().orElseThrow(GradingScaleService::missing);
    }

    private static GradingScaleResponse response(GradingScale scale) {
        return new GradingScaleResponse(scale.getId(), scale.getName(), GradingRules.highestFirst(scale.rules()).stream()
                .map(b -> new BandResponse(b.letter(), b.minMark(), b.gradePoints(), b.passing()))
                .toList());
    }

    /** e.g. "A≥70 (4.00), B≥60 (3.00), F≥0 (0.00, fail)" — compact enough for the audit log. */
    private static String describe(List<GradingRules.Band> bands) {
        return String.join(", ", GradingRules.highestFirst(bands).stream()
                .map(b -> "%s≥%s (%s%s)".formatted(b.letter(), b.minMark().stripTrailingZeros().toPlainString(),
                        b.gradePoints().setScale(2), b.passing() ? "" : ", fail"))
                .toList());
    }

    private static IllegalStateException missing() {
        return new IllegalStateException("No active grading scale; migration V5 seeds one");
    }
}
