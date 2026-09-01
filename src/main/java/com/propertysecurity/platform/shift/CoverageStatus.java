package com.propertysecurity.platform.shift;

public enum CoverageStatus {
    /** Guard clocked in and out (or out via auto-close). */
    WORKED,
    /** Guard clocked in but has not yet clocked out. */
    OPEN,
    /** Rostered slot with no clock-in at all. */
    NO_SHOW
}
