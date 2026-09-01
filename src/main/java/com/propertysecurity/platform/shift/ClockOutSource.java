package com.propertysecurity.platform.shift;

/**
 * Why it was closed in an unverifiable way — null means the server confirmed the
 * clock-out time from its own clock (the normal path). Values are stored as strings
 * so new reasons can be added without a migration.
 */
public enum ClockOutSource {
    /** clientClaimedClockOutAt arrived well after server-now, so the server cannot
     *  vouch for the time. Server derives this by comparing the claim against the
     *  request arrival time — the client supplies no flag. */
    CLIENT_CLAIMED_LATE,

    /** Shift was still open past rostered end + grace window and was closed by the
     *  scheduled auto-close job. clock_out_at is set to the rostered end time, not
     *  the time the job ran. The guard did not assert this time. */
    ROSTER_AUTO_CLOSED
}
