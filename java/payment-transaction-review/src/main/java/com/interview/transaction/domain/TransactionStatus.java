package com.interview.transaction.domain;

public enum TransactionStatus {
  AUTHORIZED,
  CAPTURED,
  REFUNDED,
  VOIDED,
  FAILED,
  SETTLED
}
