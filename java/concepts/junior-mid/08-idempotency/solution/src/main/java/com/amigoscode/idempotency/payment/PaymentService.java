package com.amigoscode.idempotency.payment;

import com.amigoscode.idempotency.customer.CustomerNotFoundException;
import com.amigoscode.idempotency.customer.CustomerRepository;
import com.amigoscode.idempotency.key.IdempotencyKeyReusedException;
import com.amigoscode.idempotency.key.IdempotencyRecord;
import com.amigoscode.idempotency.key.IdempotencyRecordRepository;
import com.amigoscode.idempotency.key.RequestHash;
import com.amigoscode.idempotency.processor.CardProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final IdempotencyRecordRepository keys;
    private final CardProcessor cardProcessor;

    public PaymentService(CustomerRepository customers, PaymentRepository payments,
                          IdempotencyRecordRepository keys, CardProcessor cardProcessor) {
        this.customers = customers;
        this.payments = payments;
        this.keys = keys;
        this.cardProcessor = cardProcessor;
    }

    /**
     * One transaction: the key row, the charge and the stored answer are committed together or not
     * at all.
     *
     * @param idempotencyKey the Idempotency-Key header, or null when the client did not send one
     */
    @Transactional
    public PaymentResult pay(String idempotencyKey, PaymentRequest request) {
        if (idempotencyKey == null) {
            // No key, no protection: the server cannot tell a retry from a second payment.
            return PaymentResult.created(charge(request));
        }
        String requestHash = RequestHash.of(request);

        // 1. Claim the key BEFORE charging. The UNIQUE constraint lets exactly one request insert
        //    the row. A second request with the same key waits here until the first one commits,
        //    and then inserts nothing.
        boolean claimed = keys.insertIfAbsent(request.customerId(), idempotencyKey, requestHash) == 1;
        IdempotencyRecord record = keys
                .findByCustomerIdAndIdempotencyKey(request.customerId(), idempotencyKey)
                .orElseThrow();

        if (!claimed) {
            // 2. Somebody already used this key. Same request: send the stored answer again.
            //    Different request: refuse, do not answer with somebody else's result.
            if (!requestHash.equals(record.getRequestHash())) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            return PaymentResult.replay(record.getResponseStatus(),
                    PaymentResponse.fromJson(record.getResponseBody()));
        }

        // 3. The key is ours: charge, and store the answer next to the key in the same transaction.
        PaymentResponse response = charge(request);
        PaymentResult result = PaymentResult.created(response);
        record.setPaymentId(response.paymentId());
        record.setResponseStatus(result.status());
        record.setResponseBody(response.toJson());
        return result;
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
