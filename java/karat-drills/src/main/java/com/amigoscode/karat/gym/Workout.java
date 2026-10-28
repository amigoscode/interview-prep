package com.amigoscode.karat.gym;

/**
 * A single workout session for a member. Start and end are minutes from the start of the day;
 * all workouts are on the same day.
 */
public class Workout {
    public final int id;
    public final int startTime;
    public final int endTime;

    public Workout(int id, int startTime, int endTime) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getDuration() {
        return endTime - startTime;
    }
}
