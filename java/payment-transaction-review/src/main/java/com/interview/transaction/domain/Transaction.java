package com.interview.transaction.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String merchantId;
  private String reference;
  private double amount;
  private double fee;
  private String currency;
  private double capturedAmount;
  private double refundedAmount;
  private double settledAmount;

  @Enumerated
  private TransactionStatus status;

  private String cardNumber;
  private String cardHolder;
  private String cvv;
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "transaction")
  private List<Refund> refunds = new ArrayList<>();

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getMerchantId() { return merchantId; }
  public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
  public String getReference() { return reference; }
  public void setReference(String reference) { this.reference = reference; }
  public double getAmount() { return amount; }
  public void setAmount(double amount) { this.amount = amount; }
  public double getFee() { return fee; }
  public void setFee(double fee) { this.fee = fee; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public double getCapturedAmount() { return capturedAmount; }
  public void setCapturedAmount(double capturedAmount) { this.capturedAmount = capturedAmount; }
  public double getRefundedAmount() { return refundedAmount; }
  public void setRefundedAmount(double refundedAmount) { this.refundedAmount = refundedAmount; }
  public double getSettledAmount() { return settledAmount; }
  public void setSettledAmount(double settledAmount) { this.settledAmount = settledAmount; }
  public TransactionStatus getStatus() { return status; }
  public void setStatus(TransactionStatus status) { this.status = status; }
  public String getCardNumber() { return cardNumber; }
  public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
  public String getCardHolder() { return cardHolder; }
  public void setCardHolder(String cardHolder) { this.cardHolder = cardHolder; }
  public String getCvv() { return cvv; }
  public void setCvv(String cvv) { this.cvv = cvv; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public List<Refund> getRefunds() { return refunds; }
  public void setRefunds(List<Refund> refunds) { this.refunds = refunds; }

  @Override
  public String toString() {
    return "Transaction{id=" + id + ", merchantId=" + merchantId + ", reference=" + reference
        + ", amount=" + amount + ", currency=" + currency + ", status=" + status
        + ", cardNumber=" + cardNumber + ", cardHolder=" + cardHolder + ", cvv=" + cvv + "}";
  }
}
