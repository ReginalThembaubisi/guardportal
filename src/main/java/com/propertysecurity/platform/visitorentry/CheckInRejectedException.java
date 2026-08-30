package com.propertysecurity.platform.visitorentry;

/** A short-code check-in was rejected for a specific, classifiable reason — see {@link CheckInRejectionReason}. */
public class CheckInRejectedException extends RuntimeException {

    private final CheckInRejectionReason reason;

    public CheckInRejectedException(CheckInRejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CheckInRejectionReason getReason() {
        return reason;
    }
}
