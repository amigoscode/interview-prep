package com.interview.transaction.messaging;

import com.interview.transaction.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);
  private static final String TOPIC = "transaction-events";

  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  public void publish(String type, Transaction transaction) {
    try {
      String payload = "{\"type\":\"" + type + "\""
          + ",\"transactionId\":" + transaction.getId()
          + ",\"merchantId\":\"" + transaction.getMerchantId() + "\""
          + ",\"reference\":\"" + transaction.getReference() + "\""
          + ",\"amount\":" + transaction.getAmount()
          + ",\"cardNumber\":\"" + transaction.getCardNumber() + "\"}";
      kafkaTemplate.send(TOPIC, payload);
    } catch (Exception e) {
      log.error("Failed to publish event");
    }
  }
}
