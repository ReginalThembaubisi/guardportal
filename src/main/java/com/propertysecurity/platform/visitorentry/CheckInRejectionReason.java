package com.propertysecurity.platform.visitorentry;

/**
 * Why a short-code check-in was rejected — a stable, machine-readable
 * counterpart to the human-readable message, so the guard-pwa frontend can
 * route each outcome (walk-in vs. re-type) without parsing prose. Nothing
 * here ever distinguishes "no such code at this property" from "no such
 * code anywhere" (there's only ever one property to look in), and nothing
 * ever indicates which digit was wrong — both are load-bearing against
 * turning the error text into an enumeration oracle over a six-digit space.
 */
public enum CheckInRejectionReason {
    EXPIRED,
    NOT_YET_VALID,
    ALREADY_USED,
    NOT_FOUND
}
