package com.amigoscode.fixit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryFeeServiceTest {

    static final ZoneId BARCELONA = ZoneId.of("Europe/Madrid");
    static final LocalTime OFF_PEAK = LocalTime.of(10, 0);

    static DeliveryFeeService serviceAt(LocalTime time) {
        Clock clock = Clock.fixed(LocalDate.of(2026, 9, 30).atTime(time).atZone(BARCELONA).toInstant(), BARCELONA);
        return new DeliveryFeeService(PricingRules.STANDARD, VoucherCatalog.standard(), clock);
    }

    static Money fee(double km, String basket, String voucher, LocalTime time) {
        return serviceAt(time).calculateFee(new DeliveryRequest(km, Money.euros(basket), voucher));
    }

    static Money eur(String amount) {
        return Money.euros(amount);
    }

    /** The original fifteen scenarios, same inputs and same expected amounts, on the new API. */
    @Nested
    class OriginalTests {

        @Test
        void shortTripPaysTheBaseFee() {
            assertThat(fee(2.0, "20.00", null, OFF_PEAK)).isEqualTo(eur("2.49"));
        }

        @Test
        void everyStartedKmAfterThreeAddsFiftyCents() {
            assertThat(fee(4.2, "20.00", null, OFF_PEAK)).isEqualTo(eur("3.49"));
        }

        @Test
        void longestAllowedTripIsAccepted() {
            assertThat(fee(15.0, "20.00", null, OFF_PEAK)).isEqualTo(eur("8.49"));
        }

        @Test
        void rejectsTripsOutOfRange() {
            assertThatThrownBy(() -> fee(0, "20.00", null, OFF_PEAK)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> fee(15.1, "20.00", null, OFF_PEAK)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void smallBasketPaysTheSmallOrderFee() {
            assertThat(fee(2.0, "8.00", null, OFF_PEAK)).isEqualTo(eur("3.49"));
        }

        @Test
        void bigBasketGetsFreeDelivery() {
            assertThat(fee(8.0, "45.00", null, OFF_PEAK)).isEqualTo(Money.ZERO);
        }

        @Test
        void basketOfExactlyThirtyGetsFreeDelivery() {
            assertThat(fee(2.0, "30.00", null, OFF_PEAK)).isEqualTo(Money.ZERO);
        }

        @Test
        void lunchPeakStartsAtNoon() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(12, 0))).isEqualTo(eur("3.49"));
        }

        @Test
        void lunchPeakIsOverAtTwo() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(14, 0))).isEqualTo(eur("2.49"));
        }

        @Test
        void dinnerPeakRunsUntilJustBeforeNine() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(20, 59))).isEqualTo(eur("3.49"));
        }

        @Test
        void freeDeliveryVoucherMakesItFree() {
            assertThat(fee(8.0, "20.00", "FREEDEL", OFF_PEAK)).isEqualTo(Money.ZERO);
        }

        @Test
        void voucherCodesAreCaseInsensitive() {
            assertThat(fee(8.0, "20.00", " freedel ", OFF_PEAK)).isEqualTo(Money.ZERO);
        }

        @Test
        void percentageVoucherRoundsHalfUpToTheCent() {
            // 3.49 * 0.85 = 2.9665 -> 2.97
            assertThat(fee(5.0, "20.00", "SAVE15", OFF_PEAK)).isEqualTo(eur("2.97"));
        }

        @Test
        void euroOffVoucherTakesOneEuroOff() {
            assertThat(fee(5.0, "20.00", "EURO1", OFF_PEAK)).isEqualTo(eur("2.49"));
        }

        @Test
        void unknownVoucherIsIgnored() {
            assertThat(fee(2.0, "20.00", "WHATEVER", OFF_PEAK)).isEqualTo(eur("2.49"));
        }
    }

    /** New tests pinning each of the four fixes from both sides of the boundary. */
    @Nested
    class Fixes {

        @Test
        void basketOneCentUnderThirtyStillPays() {
            assertThat(fee(2.0, "29.99", null, OFF_PEAK)).isEqualTo(eur("2.49"));
        }

        @Test
        void lunchPeakCoversOneFiftyNine() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(13, 59))).isEqualTo(eur("3.49"));
        }

        @Test
        void peakIsOverHalfWayThroughTheBoundaryHour() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(14, 30))).isEqualTo(eur("2.49"));
        }

        @Test
        void dinnerPeakIsOverAtNine() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(21, 0))).isEqualTo(eur("2.49"));
        }

        @Test
        void justBeforeLunchIsOffPeak() {
            assertThat(fee(2.0, "20.00", null, LocalTime.of(11, 59, 59))).isEqualTo(eur("2.49"));
        }

        @Test
        void amountVoucherIsAppliedOnceAndNeverGoesBelowZero() {
            assertThat(fee(2.0, "20.00", "EURO1", OFF_PEAK)).isEqualTo(eur("1.49"));
            Money tinyFee = new Voucher.AmountOff(eur("1.00")).applyTo(eur("0.40"));
            assertThat(tinyFee).isEqualTo(Money.ZERO);
        }

        @Test
        void percentageVoucherOnEveryFeeComponent() {
            // 2.49 + 1.00 small order + 1.00 peak = 4.49 * 0.85 = 3.8165 -> 3.82
            assertThat(fee(2.0, "8.00", "SAVE15", LocalTime.of(19, 30))).isEqualTo(eur("3.82"));
        }
    }

    @Nested
    class Edges {

        @Test
        void exactlyThreeKmHasNoExtraCharge() {
            assertThat(fee(3.0, "20.00", null, OFF_PEAK)).isEqualTo(eur("2.49"));
        }

        @Test
        void aMetrePastThreeKmStartsANewKm() {
            assertThat(fee(3.001, "20.00", null, OFF_PEAK)).isEqualTo(eur("2.99"));
        }

        @Test
        void everythingStacks() {
            // 2.49 + 2 started km (1.00) + small order 1.00 + peak 1.00
            assertThat(fee(4.5, "9.99", null, LocalTime.of(20, 0))).isEqualTo(eur("5.49"));
        }

        @Test
        void missingOrBlankVoucherIsIgnored() {
            assertThat(fee(2.0, "20.00", null, OFF_PEAK)).isEqualTo(eur("2.49"));
            assertThat(fee(2.0, "20.00", "   ", OFF_PEAK)).isEqualTo(eur("2.49"));
        }

        @Test
        void rejectsNegativeAndNonNumberDistances() {
            assertThatThrownBy(() -> fee(-1, "20.00", null, OFF_PEAK)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> fee(Double.NaN, "20.00", null, OFF_PEAK))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNegativeBasket() {
            assertThatThrownBy(() -> new DeliveryRequest(2.0, eur("-0.01"), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void usesTheClocksZoneForPeakHours() {
            // 10:30 UTC is 12:30 in Barcelona in September: lunch peak there, not in London.
            Clock utc = Clock.fixed(java.time.Instant.parse("2026-09-30T10:30:00Z"), ZoneId.of("UTC"));
            Clock barcelona = utc.withZone(BARCELONA);
            DeliveryRequest request = DeliveryRequest.of(2.0, eur("20.00"));
            assertThat(new DeliveryFeeService(PricingRules.STANDARD, VoucherCatalog.standard(), utc)
                    .calculateFee(request)).isEqualTo(eur("2.49"));
            assertThat(new DeliveryFeeService(PricingRules.STANDARD, VoucherCatalog.standard(), barcelona)
                    .calculateFee(request)).isEqualTo(eur("3.49"));
        }

        @Test
        void rulesAndVouchersAreInjected() {
            PricingRules noPeak = new PricingRules(eur("1.00"), 1, eur("0.25"), 5.0, eur("5.00"), eur("0.50"),
                    java.util.List.of(), eur("9.99"), eur("20.00"));
            VoucherCatalog half = VoucherCatalog.of(Map.of("HALF", new Voucher.PercentOff(50)));
            DeliveryFeeService service = new DeliveryFeeService(noPeak, half, Clock.systemUTC());
            // 1.00 + 2 started km * 0.25 = 1.50, half off = 0.75
            assertThat(service.calculateFee(new DeliveryRequest(2.5, eur("10.00"), "half"))).isEqualTo(eur("0.75"));
            assertThat(service.calculateFee(new DeliveryRequest(2.5, eur("10.00"), "FREEDEL"))).isEqualTo(eur("1.50"));
        }
    }
}
