package com.amigoscode.idempotency.payment;

/** The body of POST /api/payments. Amounts are in cents: 2500 is 25.00. */
public record PaymentRequest(long customerId, long amountCents) {
}
