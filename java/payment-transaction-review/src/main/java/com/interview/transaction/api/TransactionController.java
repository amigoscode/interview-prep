package com.interview.transaction.api;

import com.interview.transaction.domain.Transaction;
import com.interview.transaction.repository.TransactionRepository;
import com.interview.transaction.service.TransactionService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

  private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

  @Autowired
  private TransactionService transactionService;

  @Autowired
  private TransactionRepository transactionRepository;

  @PostMapping("/create")
  public Transaction create(@RequestBody Transaction transaction) {
    log.info("Create transaction request: {}", transaction);
    return transactionService.create(transaction);
  }

  @GetMapping("/{id}")
  public Transaction get(@PathVariable Long id) {
    return transactionRepository.findById(id).get();
  }

  @GetMapping
  public List<Transaction> list(@RequestParam String merchantId,
      @RequestParam(required = false) String status) {
    return transactionService.findByMerchant(merchantId, status);
  }

  @GetMapping("/search")
  public List<Transaction> search(@RequestParam String reference) {
    return transactionService.searchByReference(reference);
  }

  @PostMapping("/{id}/capture")
  public Transaction capture(@PathVariable Long id,
      @RequestParam(required = false) Double amount) {
    return transactionService.capture(id, amount);
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<?> refund(@PathVariable Long id, @RequestParam double amount,
      @RequestParam(required = false) String reason) {
    try {
      return ResponseEntity.ok(transactionService.refund(id, amount, reason));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(e.getMessage());
    }
  }

  @GetMapping("/{id}/void")
  public Transaction voidTransaction(@PathVariable Long id) {
    return transactionService.voidTransaction(id);
  }
}
