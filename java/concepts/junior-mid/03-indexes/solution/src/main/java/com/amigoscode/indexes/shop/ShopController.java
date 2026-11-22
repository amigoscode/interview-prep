package com.amigoscode.indexes.shop;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ShopController {

    private final ShopRepository shop;

    public ShopController(ShopRepository shop) {
        this.shop = shop;
    }

    @GetMapping("/orders/track/{trackingNumber}")
    public ResponseEntity<Map<String, Object>> trackOrder(@PathVariable String trackingNumber) {
        return ResponseEntity.of(shop.trackOrder(trackingNumber));
    }

    @GetMapping("/orders/pending")
    public List<Map<String, Object>> pendingQueue() {
        return shop.pendingQueue();
    }

    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> customerByEmail(@RequestParam String email) {
        return ResponseEntity.of(shop.customerByEmail(email));
    }

    @GetMapping("/customers/{id}/orders")
    public List<Map<String, Object>> latestOrders(@PathVariable long id) {
        return shop.latestOrders(id);
    }

    @GetMapping("/customers/{id}/spend")
    public Map<String, Object> customerSpend(@PathVariable long id) {
        return shop.customerSpend(id);
    }

    @GetMapping("/reports/daily-sales")
    public Map<String, Object> dailySales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return shop.dailySales(day);
    }

    @GetMapping("/reports/by-status")
    public Map<String, Object> salesByStatus(@RequestParam String status) {
        return shop.salesByStatus(status);
    }
}
