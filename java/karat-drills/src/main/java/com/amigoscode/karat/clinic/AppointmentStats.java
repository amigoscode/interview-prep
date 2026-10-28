package com.amigoscode.karat.clinic;

/** The result of {@link ClinicManager#getAppointmentStatistics()}. */
public class AppointmentStats {
    public final int totalAppointments;
    public final int completedAppointments;
    public final double noShowRate;

    public AppointmentStats(int totalAppointments, int completedAppointments, double noShowRate) {
        this.totalAppointments = totalAppointments;
        this.completedAppointments = completedAppointments;
        this.noShowRate = noShowRate;
    }
}
