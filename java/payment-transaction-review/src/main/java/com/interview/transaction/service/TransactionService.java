package com.interview.transaction.service;

import com.interview.transaction.domain.Refund;
import com.interview.transaction.domain.Transaction;
import com.interview.transaction.domain.TransactionStatus;
import com.interview.transaction.messaging.TransactionEventPublisher;
import com.interview.transaction.repository.RefundRepository;
import com.interview.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

  private static final double FEE_RATE = 0.029;
  private static final double FIXED_FEE = 0.30;

  @Autowired
  private TransactionRepository transactionRepository;

  @Autowired
  private RefundRepository refundRepository;

  @Autowired
  private TransactionEventPublisher eventPublisher;

  @PersistenceContext
  private EntityManager entityManager;

  // merchants retry when they get a timeout, so remember what we already created
  private final Map<String, Transaction> recentByReference = new HashMap<>();

  public Transaction create(Transaction transaction) {
    if (recentByReference.containsKey(transaction.getReference())) {
      return recentByReference.get(transaction.getReference());
    }
    if (transaction.getAmount() <= 0) {
      return null;
    }
    transaction.setStatus(TransactionStatus.AUTHORIZED);
    transaction.setFee(transaction.getAmount() * FEE_RATE + FIXED_FEE);
    transaction.setCreatedAt(LocalDateTime.now());
    Transaction saved = transactionRepository.save(transaction);
    recentByReference.put(saved.getReference(), saved);
    eventPublisher.publish("TRANSACTION_AUTHORIZED", saved);
    return saved;
  }

  public List<Transaction> findByMerchant(String merchantId, String status) {
    return transactionRepository.findAll().stream()
        .filter(t -> t.getMerchantId().equals(merchantId))
        .filter(t -> status == null || t.getStatus().name() == status)
        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  public List<Transaction> searchByReference(String reference) {
    String sql = "SELECT * FROM transactions WHERE reference LIKE '%" + reference + "%'";
    return entityManager.createNativeQuery(sql, Transaction.class).getResultList();
  }

  @Transactional
  public Transaction capture(Long id, Double amount) {
    Transaction transaction = transactionRepository.findById(id).get();
    if (transaction.getStatus() != TransactionStatus.AUTHORIZED) {
      throw new RuntimeException("Transaction cannot be captured");
    }
    double toCapture = amount == null ? transaction.getAmount() : amount;
    transaction.setCapturedAmount(toCapture);
    transaction.setStatus(TransactionStatus.CAPTURED);
    transactionRepository.save(transaction);
    eventPublisher.publish("TRANSACTION_CAPTURED", transaction);
    return transaction;
  }

  public Refund refund(Long id, double amount, String reason) throws Exception {
    Transaction transaction = transactionRepository.findById(id).get();
    if (transaction.getStatus() == TransactionStatus.CAPTURED
        || transaction.getStatus() == TransactionStatus.REFUNDED) {
      if (transaction.getRefundedAmount() + amount > transaction.getCapturedAmount()) {
        throw new Exception("Refund exceeds captured amount");
      }
      return saveRefund(transaction, amount, reason);
    }
    throw new Exception("Transaction is not refundable");
  }

  @Transactional
  private Refund saveRefund(Transaction transaction, double amount, String reason) {
    Refund refund = new Refund();
    refund.setTransaction(transaction);
    refund.setAmount(amount);
    refund.setReason(reason);
    refund.setCreatedAt(LocalDateTime.now());
    refundRepository.save(refund);
    eventPublisher.publish("REFUND_CREATED", transaction);

    transaction.setRefundedAmount(transaction.getRefundedAmount() + amount);
    transaction.setStatus(TransactionStatus.REFUNDED);
    transactionRepository.save(transaction);
    return refund;
  }

  public Transaction voidTransaction(Long id) {
    Transaction transaction = transactionRepository.findById(id).get();
    transaction.setStatus(TransactionStatus.VOIDED);
    return transactionRepository.save(transaction);
  }
}
