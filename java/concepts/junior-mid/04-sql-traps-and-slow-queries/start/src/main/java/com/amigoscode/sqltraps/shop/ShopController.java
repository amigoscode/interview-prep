package com.amigoscode.sqltraps.shop;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
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

    @GetMapping("/reports/paid-orders")
    public List<Map<String, Object>> paidOrdersPerCustomer() {
        return shop.paidOrdersPerCustomer();
    }

    @GetMapping("/reports/never-ordered")
    public List<Map<String, Object>> customersWhoNeverOrdered() {
        return shop.customersWhoNeverOrdered();
    }

    @GetMapping("/reports/revenue")
    public List<Map<String, Object>> revenuePerCustomer() {
        return shop.revenuePerCustomer();
    }

    @GetMapping("/reports/big-spenders")
    public List<Map<String, Object>> bigSpenders(@RequestParam(defaultValue = "10000") long min) {
        return shop.bigSpenders(min);
    }

    @GetMapping("/reports/country-spend")
    public List<Map<String, Object>> countrySpend(@RequestParam String country) {
        return shop.countrySpend(country);
    }

    @GetMapping("/orders/recent")
    public List<Map<String, Object>> recentOrders(@RequestParam(defaultValue = "20") int size) {
        return shop.recentOrders(size);
    }

    @GetMapping("/orders/expired")
    public List<Map<String, Object>> expiredOrders() {
        return shop.expiredOrders();
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> ordersOfDay(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return shop.ordersOfDay(day);
    }

    @GetMapping("/customers/{id}/orders")
    public List<Map<String, Object>> ordersOfCustomer(@PathVariable long id) {
        return shop.ordersOfCustomer(id);
    }
}
