package com.amigoscode.karat.clinic;

/** An appointment of a patient with a doctor. */
public class Appointment {
    public final int appointmentId;
    public final int doctorId;
    public final int patientId;
    public final int durationMinutes;
    public AppointmentStatus status;

    public Appointment(int appointmentId, int doctorId, int patientId, int durationMinutes,
                       AppointmentStatus status) {
        this.appointmentId = appointmentId;
        this.doctorId = doctorId;
        this.patientId = patientId;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }
}
