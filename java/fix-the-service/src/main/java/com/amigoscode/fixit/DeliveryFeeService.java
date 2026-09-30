package com.amigoscode.fixit;

import java.time.LocalTime;

/**
 * Calculates the delivery fee for an order. It is in production and customers are being charged
 * the wrong amount: four tests in {@code DeliveryFeeServiceTest} fail.
 *
 * <p>Pricing rules, as agreed with the product team (the tests encode these):
 * <ol>
 *   <li>Distance must be more than 0 km and at most 15 km, otherwise the order is rejected.</li>
 *   <li>The base fee is 2.49 and covers the first 3 km. Every started km after that adds 0.50.</li>
 *   <li>Baskets under 10.00 pay a small-order fee of 1.00.</li>
 *   <li>Peak hours add 1.00. Peak is 12:00 to 14:00 and 19:00 to 21:00, start included,
 *       end excluded.</li>
 *   <li>Baskets of 30.00 or more get free delivery: the fee is 0.00.</li>
 *   <li>Voucher codes are case-insensitive. Unknown or missing codes are ignored.
 *     <ul>
 *       <li>{@code FREEDEL}: the fee becomes 0.00</li>
 *       <li>{@code SAVE15}: 15% off the fee</li>
 *       <li>{@code EURO1}: 1.00 off the fee, never below 0.00</li>
 *     </ul>
 *   </li>
 *   <li>The result is rounded to the cent, half up.</li>
 * </ol>
 */
public class DeliveryFeeService {

    public double calculateFee(double d, double b, String v) {
        return calculateFee(d, b, v, LocalTime.now());
    }

    // added so the tests can pass a time
    public double calculateFee(double d, double b, String v, LocalTime t) {
        System.out.println("calculateFee called d=" + d + " b=" + b + " v=" + v + " t=" + t);

        // check distance
        if (d <= 0) {
            System.out.println("ERROR bad distance " + d);
            throw new IllegalArgumentException("bad distance");
        }
        if (d > 15) {
            System.out.println("ERROR too far " + d);
            throw new IllegalArgumentException("too far");
        }

        // check basket
        if (b < 0) {
            System.out.println("ERROR bad basket " + b);
            throw new IllegalArgumentException("bad basket");
        }

        double f = 2.49;

        // extra km
        if (d > 3) {
            double extra = d - 3;
            int km = (int) Math.ceil(extra);
            f = f + km * 0.5;
            System.out.println("extra km: " + km + " fee now " + f);
        }

        // small basket
        if (b < 10) {
            f = f + 1.0;
            System.out.println("small basket, fee now " + f);
        }

        // peak
        int h = t.getHour();
        boolean p = false;
        if (h >= 12 && h <= 14) {
            p = true;
        }
        if (h >= 19 && h < 21) {
            p = true;
        }
        if (p) {
            f = f + 1.0;
            System.out.println("peak, fee now " + f);
        }

        // free delivery
        if (b > 30) {
            f = 0;
            System.out.println("free delivery");
        }

        // voucher
        double disc = 0;
        try {
            String code = v.trim().toUpperCase();
            if (code.equals("FREEDEL")) {
                f = 0;
                System.out.println("voucher FREEDEL");
            } else if (code.equals("SAVE15")) {
                f = f * 0.85;
                System.out.println("voucher SAVE15, fee now " + f);
            } else if (code.equals("EURO1")) {
                disc = 1.0;
                f = f - disc;
                System.out.println("voucher EURO1, fee now " + f);
            } else {
                System.out.println("unknown voucher " + code);
            }
        } catch (Exception e) {
            // ignore
        }

        // apply discount
        if (disc > 0) {
            f = f - disc;
        }
        if (f < 0) {
            f = 0;
        }

        // round to cents
        f = Math.floor(f * 100) / 100;

        System.out.println("final fee " + f);
        return f;
    }
}
