package com.amigoscode.locking.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    public record ProductResponse(long id, String name, int stock) {
    }

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return new ProductResponse(product.getId(), product.getName(), product.getStock());
    }
}
