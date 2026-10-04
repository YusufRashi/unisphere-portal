package com.unisphere.common.exception;

import com.unisphere.common.web.RequestIdFilter;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Builds RFC 9457 Problem Details with the extra fields every UniSphere error carries. */
@Component
public class ProblemDetailFactory {

    private static final String TYPE_BASE = "https://unisphere.dev/problems/";

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        enrich(problem, code);
        return problem;
    }

    /** Adds type, title, code, timestamp and request id to an existing problem (possibly created by Spring). */
    public ProblemDetail enrich(ProblemDetail problem, ErrorCode code) {
        problem.setType(URI.create(TYPE_BASE + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", clock.instant().toString());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
