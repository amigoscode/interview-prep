package com.amigoscode.dispatch;

/** A point on the Earth in decimal degrees. Story 1 starts here. */
public record Location(double lat, double lon) {

    /** Distance to {@code other} in kilometres. Haversine or equirectangular: say which, and why. */
    public double distanceKmTo(Location other) {
        throw new UnsupportedOperationException("story 1");
    }
}
