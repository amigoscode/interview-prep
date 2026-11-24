package com.interview.transaction.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interview.transaction.domain.Refund;
import com.interview.transaction.domain.Transaction;
import com.interview.transaction.domain.TransactionStatus;
import com.interview.transaction.messaging.TransactionEventPublisher;
import com.interview.transaction.repository.RefundRepository;
import com.interview.transaction.repository.TransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private RefundRepository refundRepository;

  @Mock
  private TransactionEventPublisher eventPublisher;

  @InjectMocks
  private TransactionService transactionService;

  @Test
  void createTransaction() {
    Transaction transaction = new Transaction();
    transaction.setMerchantId("merchant-a");
    transaction.setReference("order-1");
    transaction.setAmount(10.00);
    when(transactionRepository.save(any())).thenReturn(transaction);

    Transaction result = transactionService.create(transaction);

    assertNotNull(result);
  }

  @Test
  void refundTransaction() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setStatus(TransactionStatus.CAPTURED);
    transaction.setCapturedAmount(100.00);
    when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

    Refund refund = transactionService.refund(1L, 40.00, "customer request");

    assertNotNull(refund);
    verify(transactionRepository).save(any());
  }

  @Test
  @Disabled("flaky on CI, fix later")
  void twoRefundsAtTheSameTime() throws Exception {
    Transaction transaction = new Transaction();
    transaction.setStatus(TransactionStatus.CAPTURED);
    transaction.setCapturedAmount(100.00);
    when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

    Thread first = new Thread(() -> refundQuietly(80.00));
    Thread second = new Thread(() -> refundQuietly(80.00));
    first.start();
    second.start();
    first.join();
    second.join();
  }

  private void refundQuietly(double amount) {
    try {
      transactionService.refund(1L, amount, "test");
    } catch (Exception e) {
      // ignore
    }
  }
}
