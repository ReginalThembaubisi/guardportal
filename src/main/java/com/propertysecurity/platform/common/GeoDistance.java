package com.propertysecurity.platform.common;

import java.math.BigDecimal;

/** Great-circle distance between two lat/lng points, via the haversine formula. */
public final class GeoDistance {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoDistance() {
    }

    public static int metersBetween(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double phi1 = Math.toRadians(lat1.doubleValue());
        double phi2 = Math.toRadians(lat2.doubleValue());
        double deltaPhi = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLambda = Math.toRadians(lng2.subtract(lng1).doubleValue());

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return (int) Math.round(EARTH_RADIUS_METERS * c);
    }
}
