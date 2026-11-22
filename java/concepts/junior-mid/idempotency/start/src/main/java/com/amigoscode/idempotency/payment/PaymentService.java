package com.amigoscode.idempotency.payment;

import com.amigoscode.idempotency.customer.CustomerNotFoundException;
import com.amigoscode.idempotency.customer.CustomerRepository;
import com.amigoscode.idempotency.key.IdempotencyRecordRepository;
import com.amigoscode.idempotency.processor.CardProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final IdempotencyRecordRepository keys;   // not used yet: you need it from Task 2 on
    private final CardProcessor cardProcessor;

    public PaymentService(CustomerRepository customers, PaymentRepository payments,
                          IdempotencyRecordRepository keys, CardProcessor cardProcessor) {
        this.customers = customers;
        this.payments = payments;
        this.keys = keys;
        this.cardProcessor = cardProcessor;
    }

    /**
     * THE BUG IS HERE. The key is ignored, so every copy of a request is a new payment.
     *
     * <p>TODO Task 2: if this customer already used this key, do not charge again: answer with the
     * payment that already exists. Otherwise charge, and remember the key.
     *
     * <p>TODO Task 4: claim the key BEFORE charging, with an INSERT that the database refuses for
     * the second request (see schema.sql and IdempotencyRecordRepository).
     *
     * <p>TODO Task 6: store the status code and the body with the key, and answer a retry with
     * exactly that (PaymentResult.replay).
     *
     * <p>TODO Task 7: store RequestHash.of(request) with the key. A key that comes back with a
     * different hash is an IdempotencyKeyReusedException.
     *
     * @param idempotencyKey the Idempotency-Key header, or null when the client did not send one
     */
    @Transactional
    public PaymentResult pay(String idempotencyKey, PaymentRequest request) {
        return PaymentResult.created(charge(request));
    }

    /** The payment itself: ask the card processor, take the money, write the payment row. */
    private PaymentResponse charge(PaymentRequest request) {
        long customerId = request.customerId();
        long amountCents = request.amountCents();

        long balance = customers.findBalance(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (balance < amountCents) {
            throw new InsufficientFundsException(customerId, amountCents);
        }
        cardProcessor.charge(customerId, amountCents);            // about 100 ms, cannot be rolled back
        if (customers.debit(customerId, amountCents) == 0) {
            throw new InsufficientFundsException(customerId, amountCents);
        }
        Payment payment = payments.save(new Payment(customerId, amountCents));
        long balanceAfter = customers.findBalance(customerId).orElseThrow();
        return new PaymentResponse(payment.getId(), customerId, amountCents, balanceAfter);
    }
}
