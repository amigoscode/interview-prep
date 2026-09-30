package com.amigoscode.dispatch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Story 2. */
class ShiftScheduleTest {

    static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    static Shift shift(String start, String end) {
        return new Shift(t(start), t(end));
    }

    @Nested
    class Merge {

        @Test
        void overlappingShiftsBecomeOne() {
            assertThat(ShiftSchedule.merge(List.of(shift("10:00", "13:00"), shift("12:00", "15:00"))))
                    .containsExactly(shift("10:00", "15:00"));
        }

        @Test
        void touchingShiftsBecomeOne() {
            assertThat(ShiftSchedule.merge(List.of(shift("10:00", "12:00"), shift("12:00", "14:00"))))
                    .containsExactly(shift("10:00", "14:00"));
        }

        @Test
        void aShiftInsideAnotherDisappears() {
            assertThat(ShiftSchedule.merge(List.of(shift("09:00", "17:00"), shift("11:00", "12:00"))))
                    .containsExactly(shift("09:00", "17:00"));
        }

        @Test
        void unsortedInputWithGapsIsSortedAndGapsKept() {
            assertThat(ShiftSchedule.merge(List.of(
                    shift("18:00", "22:00"),
                    shift("08:00", "10:00"),
                    shift("11:00", "14:00"),
                    shift("09:30", "11:00"),
                    shift("21:00", "23:00"))))
                    .containsExactly(shift("08:00", "14:00"), shift("18:00", "23:00"));
        }

        @Test
        void emptyAndSingleInputs() {
            assertThat(ShiftSchedule.merge(List.of())).isEmpty();
            assertThat(ShiftSchedule.merge(List.of(shift("10:00", "11:00")))).containsExactly(shift("10:00", "11:00"));
        }

        @Test
        void rejectsEmptyOrBackwardsShifts() {
            assertThatThrownBy(() -> shift("12:00", "12:00")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> shift("14:00", "12:00")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Shift(null, t("12:00"))).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class OnShift {

        @Test
        void addedShiftsAreStoredMerged() {
            ShiftSchedule schedule = new ShiftSchedule();
            schedule.addShift("r1", t("12:00"), t("14:00"));
            schedule.addShift("r1", t("10:00"), t("12:00"));
            schedule.addShift("r1", t("18:00"), t("21:00"));

            assertThat(schedule.shiftsOf("r1")).containsExactly(shift("10:00", "14:00"), shift("18:00", "21:00"));
        }

        @Test
        void startIsInclusiveAndEndIsExclusive() {
            ShiftSchedule schedule = new ShiftSchedule();
            schedule.addShift("r1", t("10:00"), t("14:00"));

            assertThat(schedule.isOnShift("r1", t("09:59"))).isFalse();
            assertThat(schedule.isOnShift("r1", t("10:00"))).isTrue();
            assertThat(schedule.isOnShift("r1", t("13:59:59"))).isTrue();
            assertThat(schedule.isOnShift("r1", t("14:00"))).isFalse();
        }

        @Test
        void theJoinOfTwoTouchingShiftsIsCovered() {
            ShiftSchedule schedule = new ShiftSchedule();
            schedule.addShift("r1", t("10:00"), t("12:00"));
            schedule.addShift("r1", t("12:00"), t("14:00"));
            assertThat(schedule.isOnShift("r1", t("12:00"))).isTrue();
        }

        @Test
        void aGapBetweenShiftsIsOffShift() {
            ShiftSchedule schedule = new ShiftSchedule();
            schedule.addShift("r1", t("08:00"), t("11:00"));
            schedule.addShift("r1", t("17:00"), t("22:00"));

            assertThat(schedule.isOnShift("r1", t("14:00"))).isFalse();
            assertThat(schedule.isOnShift("r1", t("17:30"))).isTrue();
            assertThat(schedule.isOnShift("r1", t("23:00"))).isFalse();
        }

        @Test
        void unknownRiderIsNeverOnShift() {
            ShiftSchedule schedule = new ShiftSchedule();
            assertThat(schedule.shiftsOf("ghost")).isEmpty();
            assertThat(schedule.isOnShift("ghost", t("12:00"))).isFalse();
        }

        @Test
        void ridersDoNotShareShifts() {
            ShiftSchedule schedule = new ShiftSchedule();
            schedule.addShift("r1", t("10:00"), t("14:00"));
            assertThat(schedule.isOnShift("r2", t("12:00"))).isFalse();
        }

        @Test
        void anOvernightShiftIsTwoShifts() {
            ShiftSchedule schedule = new ShiftSchedule();
            schedule.addShift("r1", t("22:00"), LocalTime.MAX);
            schedule.addShift("r1", LocalTime.MIDNIGHT, t("02:00"));

            assertThat(schedule.isOnShift("r1", t("23:59:59"))).isTrue();
            assertThat(schedule.isOnShift("r1", LocalTime.MIDNIGHT)).isTrue();
            assertThat(schedule.isOnShift("r1", t("02:00"))).isFalse();
        }

        @Test
        void rejectsBlankRiderId() {
            ShiftSchedule schedule = new ShiftSchedule();
            assertThatThrownBy(() -> schedule.addShift(" ", t("10:00"), t("11:00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
