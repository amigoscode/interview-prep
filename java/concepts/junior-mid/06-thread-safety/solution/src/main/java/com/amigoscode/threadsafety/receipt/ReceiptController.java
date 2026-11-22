package com.amigoscode.threadsafety.receipt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService receipts;

    public ReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @GetMapping
    public Receipt receipt(@RequestParam String customer, @RequestParam int amountCents) {
        return receipts.receiptFor(customer, amountCents);
    }
}
