package com.amigoscode.karat.clinic;

/**
 * One patient's visit to a doctor. The start time is minutes from the start of the day, the
 * duration is in minutes.
 */
public class PatientVisit {
    public final int visitId;
    public final int patientId;
    public final int doctorId;
    public final int startTime;
    public final int durationMinutes;
    public final VisitType visitType;

    public PatientVisit(int visitId, int patientId, int doctorId, int startTime,
                        int durationMinutes, VisitType visitType) {
        this.visitId = visitId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.visitType = visitType;
    }
}
