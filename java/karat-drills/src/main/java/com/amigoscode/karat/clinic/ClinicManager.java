package com.amigoscode.karat.clinic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * We are building the back-end for a clinic appointment management system. The system tracks
 * doctors, appointments and patient visits. A {@link Doctor} has an ID and a name; an
 * {@link Appointment} has a doctor, a patient, a duration and a status (SCHEDULED, COMPLETED,
 * CANCELLED or NO_SHOW). This class manages doctors and appointments and provides statistics.
 *
 * <p>Part 1: {@code ClinicManagerTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: we are extending the platform to track patient visits to doctors. Implement
 * {@link #addPatientVisit} and {@link #getAverageVisitDurationByType}. Where the visits are
 * stored is up to you.
 */
public class ClinicManager {

    public final Map<Integer, Doctor> doctors = new HashMap<>();
    public final List<Appointment> appointments = new ArrayList<>();

    /** Visits live here, keyed by doctor ID, so a doctor's visits are found without a scan. */
    private final Map<Integer, List<PatientVisit>> visitsByDoctor = new HashMap<>();

    public void addDoctor(Doctor doctor) {
        doctors.put(doctor.doctorId, doctor);
    }

    public void addAppointment(Appointment appointment) {
        appointments.add(appointment);
    }

    /**
     * Returns the number of appointments, the number of completed appointments and the no-show
     * rate: NO_SHOW appointments as a fraction of all appointments, for example 0.2.
     */
    public AppointmentStats getAppointmentStatistics() {
        int total = appointments.size();
        int completed = 0;
        int noShows = 0;
        for (Appointment a : appointments) {
            if (a.status == AppointmentStatus.COMPLETED) {
                completed++;
            }
            // Bug was counting every non-COMPLETED appointment (CANCELLED, SCHEDULED too) as a no-show.
            if (a.status == AppointmentStatus.NO_SHOW) {
                noShows++;
            }
        }
        double noShowRate = total > 0 ? (double) noShows / total : 0.0;
        return new AppointmentStats(total, completed, noShowRate);
    }

    /**
     * Part 2.1: stores a visit. If the visit's doctorId does not refer to a known doctor in this
     * manager, the visit is ignored.
     */
    public void addPatientVisit(PatientVisit visit) {
        if (!doctors.containsKey(visit.doctorId)) {
            return;
        }
        visitsByDoctor.computeIfAbsent(visit.doctorId, id -> new ArrayList<>()).add(visit);
    }

    /**
     * Part 2.2: the doctor's average visit duration in minutes for each visit type, as a map from
     * visit type (CONSULTATION, FOLLOWUP, EMERGENCY) to average.
     *
     * <ol>
     *   <li>Only visit types the doctor has at least one visit for appear in the map.
     *   <li>If the doctor has no visits at all, return an empty map.
     * </ol>
     */
    public Map<VisitType, Double> getAverageVisitDurationByType(int doctorId) {
        Map<VisitType, Integer> totals = new EnumMap<>(VisitType.class);
        Map<VisitType, Integer> counts = new EnumMap<>(VisitType.class);
        for (PatientVisit visit : visitsByDoctor.getOrDefault(doctorId, List.of())) {
            totals.merge(visit.visitType, visit.durationMinutes, Integer::sum);
            counts.merge(visit.visitType, 1, Integer::sum);
        }
        Map<VisitType, Double> averages = new EnumMap<>(VisitType.class);
        for (Map.Entry<VisitType, Integer> entry : totals.entrySet()) {
            VisitType type = entry.getKey();
            averages.put(type, (double) entry.getValue() / counts.get(type));
        }
        return averages;
    }
}
