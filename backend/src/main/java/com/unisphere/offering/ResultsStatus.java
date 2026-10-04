package com.unisphere.offering;

/** Lifecycle of an offering's marks: lecturers edit in DRAFT, the registrar finalizes or returns SUBMITTED marks. */
public enum ResultsStatus {
    DRAFT,
    SUBMITTED,
    FINALIZED
}
