package com.ryan.logisticexpress.dispatch.enums;

public enum StopStatus {
    PENDING,
    ARRIVED,
    DELIVERED,
    FAILED,
    REFUSED,
    SKIPPED;

    public boolean isActive() {
        return this == PENDING || this == ARRIVED;
    }
}
