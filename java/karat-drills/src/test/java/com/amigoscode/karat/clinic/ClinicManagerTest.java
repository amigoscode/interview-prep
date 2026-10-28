package com.amigoscode.karat.clinic;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class ClinicManagerTest {

    @Test
    void appointmentStatistics() {
        ClinicManager cm = new ClinicManager();
        cm.addAppointment(new Appointment(1, 10, 100, 30, AppointmentStatus.COMPLETED));
        cm.addAppointment(new Appointment(2, 10, 101, 45, AppointmentStatus.COMPLETED));
        cm.addAppointment(new Appointment(3, 10, 102, 30, AppointmentStatus.NO_SHOW));
        cm.addAppointment(new Appointment(4, 10, 103, 60, AppointmentStatus.CANCELLED));
        cm.addAppointment(new Appointment(5, 10, 104, 30, AppointmentStatus.SCHEDULED));

        AppointmentStats stats = cm.getAppointmentStatistics();

        assertThat(stats.totalAppointments).isEqualTo(5);
        assertThat(stats.completedAppointments).isEqualTo(2);
        assertThat(stats.noShowRate).isCloseTo(0.2, within(1e-4));
    }

    @Test
    void emptyClinicHasZeroStatistics() {
        AppointmentStats stats = new ClinicManager().getAppointmentStatistics();

        assertThat(stats.totalAppointments).isZero();
        assertThat(stats.completedAppointments).isZero();
        assertThat(stats.noShowRate).isZero();
    }

    @Test
    void allNoShowsGivesRateOfOne() {
        ClinicManager cm = new ClinicManager();
        cm.addAppointment(new Appointment(1, 10, 100, 30, AppointmentStatus.NO_SHOW));
        cm.addAppointment(new Appointment(2, 10, 101, 45, AppointmentStatus.NO_SHOW));

        AppointmentStats stats = cm.getAppointmentStatistics();

        assertThat(stats.completedAppointments).isZero();
        assertThat(stats.noShowRate).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void addPatientVisit() {
        ClinicManager cm = new ClinicManager();
        cm.addDoctor(new Doctor(1, "dr_smith"));
        cm.addDoctor(new Doctor(2, "dr_jones"));

        cm.addPatientVisit(new PatientVisit(1, 100, 1, 540, 30, VisitType.CONSULTATION));
        cm.addPatientVisit(new PatientVisit(2, 101, 2, 600, 45, VisitType.FOLLOWUP));
        // unknown doctor ignored
        cm.addPatientVisit(new PatientVisit(3, 102, 99, 660, 30, VisitType.EMERGENCY));

        // valid visits were stored
        Map<VisitType, Double> avg1 = cm.getAverageVisitDurationByType(1);
        assertThat(avg1).containsKey(VisitType.CONSULTATION);
        assertThat(avg1.get(VisitType.CONSULTATION)).isCloseTo(30.0, within(1e-4));

        Map<VisitType, Double> avg2 = cm.getAverageVisitDurationByType(2);
        assertThat(avg2).containsKey(VisitType.FOLLOWUP);
        assertThat(avg2.get(VisitType.FOLLOWUP)).isCloseTo(45.0, within(1e-4));

        // unknown doctor's visit was ignored
        cm.addDoctor(new Doctor(99, "dr_unknown"));
        assertThat(cm.getAverageVisitDurationByType(99)).isEmpty();
    }

    @Test
    void averageVisitDurationByType() {
        ClinicManager cm = new ClinicManager();
        cm.addDoctor(new Doctor(1, "dr_smith"));
        cm.addDoctor(new Doctor(2, "dr_jones"));

        cm.addPatientVisit(new PatientVisit(1, 100, 1, 540, 30, VisitType.CONSULTATION));
        cm.addPatientVisit(new PatientVisit(2, 101, 1, 600, 50, VisitType.CONSULTATION));
        cm.addPatientVisit(new PatientVisit(3, 102, 1, 660, 20, VisitType.FOLLOWUP));
        cm.addPatientVisit(new PatientVisit(4, 103, 2, 540, 40, VisitType.EMERGENCY));

        Map<VisitType, Double> avg1 = cm.getAverageVisitDurationByType(1);
        assertThat(avg1.get(VisitType.CONSULTATION)).isCloseTo(40.0, within(1e-4)); // (30+50)/2
        assertThat(avg1.get(VisitType.FOLLOWUP)).isCloseTo(20.0, within(1e-4));
        assertThat(avg1).doesNotContainKey(VisitType.EMERGENCY);

        Map<VisitType, Double> avg2 = cm.getAverageVisitDurationByType(2);
        assertThat(avg2.get(VisitType.EMERGENCY)).isCloseTo(40.0, within(1e-4));
        assertThat(avg2).doesNotContainKey(VisitType.CONSULTATION);
        assertThat(avg2).doesNotContainKey(VisitType.FOLLOWUP);

        // doctor with no visits
        cm.addDoctor(new Doctor(3, "dr_brown"));
        assertThat(cm.getAverageVisitDurationByType(3)).isEmpty();
    }

    @Test
    void doctorNeverAddedHasEmptyAverages() {
        ClinicManager cm = new ClinicManager();
        cm.addDoctor(new Doctor(1, "dr_smith"));
        cm.addPatientVisit(new PatientVisit(1, 100, 1, 540, 30, VisitType.CONSULTATION));

        assertThat(cm.getAverageVisitDurationByType(42)).isEmpty();
        assertThat(cm.getAverageVisitDurationByType(1)).containsOnlyKeys(VisitType.CONSULTATION);
    }
}
