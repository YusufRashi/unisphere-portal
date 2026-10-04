package com.unisphere.result;

/** Class size and the number of students with both marks entered, for one offering. */
public record ClassProgress(Long offeringId, Long classSize, Long complete) {

    public static ClassProgress empty(Long offeringId) {
        return new ClassProgress(offeringId, 0L, 0L);
    }
}
