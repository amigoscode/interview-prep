package com.amigoscode.dispatch;

/**
 * A point on the Earth in decimal degrees.
 *
 * <p>Distances use the haversine formula on a spherical Earth (radius 6,371 km). It is exact enough
 * for dispatch (error well under 0.5%), handles the antimeridian and the poles, and costs a few
 * trig calls. The equirectangular approximation is cheaper and fine inside one city, but it drifts
 * with distance and breaks across the 180th meridian unless you normalise the longitude delta.
 */
public record Location(double lat, double lon) {

    static final double EARTH_RADIUS_KM = 6_371.0;

    public Location {
        if (!(lat >= -90 && lat <= 90)) {
            throw new IllegalArgumentException("latitude must be in [-90, 90]: " + lat);
        }
        if (!(lon >= -180 && lon <= 180)) {
            throw new IllegalArgumentException("longitude must be in [-180, 180]: " + lon);
        }
    }

    /** Great-circle distance to {@code other} in kilometres (haversine). */
    public double distanceKmTo(Location other) {
        double phi1 = Math.toRadians(lat);
        double phi2 = Math.toRadians(other.lat);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(other.lon - lon);
        double a = Math.pow(Math.sin(dPhi / 2), 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.pow(Math.sin(dLambda / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
