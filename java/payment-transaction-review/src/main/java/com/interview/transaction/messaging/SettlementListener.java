package com.interview.transaction.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.transaction.domain.Transaction;
import com.interview.transaction.domain.TransactionStatus;
import com.interview.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The acquirer sends one message per settled transaction:
 * {"transactionId": 1, "amount": 49.99}
 */
@Component
public class SettlementListener {

  private static final Logger log = LoggerFactory.getLogger(SettlementListener.class);

  @Autowired
  private TransactionRepository transactionRepository;

  @KafkaListener(topics = "settlements", groupId = "transaction-service")
  public void onSettlement(String message) {
    try {
      JsonNode node = new ObjectMapper().readTree(message);
      Long transactionId = node.get("transactionId").asLong();
      double amount = node.get("amount").asDouble();

      Transaction transaction = transactionRepository.findById(transactionId).get();
      transaction.setSettledAmount(transaction.getSettledAmount() + amount);
      transaction.setStatus(TransactionStatus.SETTLED);
      transactionRepository.save(transaction);
    } catch (Exception e) {
      log.warn("Could not process settlement {}", message);
    }
  }
}
