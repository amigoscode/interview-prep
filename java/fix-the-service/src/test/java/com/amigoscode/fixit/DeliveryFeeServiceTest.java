package com.amigoscode.fixit;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Four of these fail on purpose. That is Task 1. Fix the code, not the tests. */
class DeliveryFeeServiceTest {

    static final LocalTime OFF_PEAK = LocalTime.of(10, 0);

    DeliveryFeeService service = new DeliveryFeeService();

    @Test
    void shortTripPaysTheBaseFee() {
        assertThat(service.calculateFee(2.0, 20.00, null, OFF_PEAK)).isEqualTo(2.49);
    }

    @Test
    void everyStartedKmAfterThreeAddsFiftyCents() {
        assertThat(service.calculateFee(4.2, 20.00, null, OFF_PEAK)).isEqualTo(3.49);
    }

    @Test
    void longestAllowedTripIsAccepted() {
        assertThat(service.calculateFee(15.0, 20.00, null, OFF_PEAK)).isEqualTo(8.49);
    }

    @Test
    void rejectsTripsOutOfRange() {
        assertThatThrownBy(() -> service.calculateFee(0, 20.00, null, OFF_PEAK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateFee(15.1, 20.00, null, OFF_PEAK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void smallBasketPaysTheSmallOrderFee() {
        assertThat(service.calculateFee(2.0, 8.00, null, OFF_PEAK)).isEqualTo(3.49);
    }

    @Test
    void bigBasketGetsFreeDelivery() {
        assertThat(service.calculateFee(8.0, 45.00, null, OFF_PEAK)).isEqualTo(0.0);
    }

    @Test
    void basketOfExactlyThirtyGetsFreeDelivery() {
        assertThat(service.calculateFee(2.0, 30.00, null, OFF_PEAK)).isEqualTo(0.0);
    }

    @Test
    void lunchPeakStartsAtNoon() {
        assertThat(service.calculateFee(2.0, 20.00, null, LocalTime.of(12, 0))).isEqualTo(3.49);
    }

    @Test
    void lunchPeakIsOverAtTwo() {
        assertThat(service.calculateFee(2.0, 20.00, null, LocalTime.of(14, 0))).isEqualTo(2.49);
    }

    @Test
    void dinnerPeakRunsUntilJustBeforeNine() {
        assertThat(service.calculateFee(2.0, 20.00, null, LocalTime.of(20, 59))).isEqualTo(3.49);
    }

    @Test
    void freeDeliveryVoucherMakesItFree() {
        assertThat(service.calculateFee(8.0, 20.00, "FREEDEL", OFF_PEAK)).isEqualTo(0.0);
    }

    @Test
    void voucherCodesAreCaseInsensitive() {
        assertThat(service.calculateFee(8.0, 20.00, " freedel ", OFF_PEAK)).isEqualTo(0.0);
    }

    @Test
    void percentageVoucherRoundsHalfUpToTheCent() {
        // 3.49 * 0.85 = 2.9665 -> 2.97
        assertThat(service.calculateFee(5.0, 20.00, "SAVE15", OFF_PEAK)).isEqualTo(2.97);
    }

    @Test
    void euroOffVoucherTakesOneEuroOff() {
        assertThat(service.calculateFee(5.0, 20.00, "EURO1", OFF_PEAK)).isEqualTo(2.49);
    }

    @Test
    void unknownVoucherIsIgnored() {
        assertThat(service.calculateFee(2.0, 20.00, "WHATEVER", OFF_PEAK)).isEqualTo(2.49);
    }
}
