package com.amigoscode.karat.snippets;

/**
 * Theory question: "What is missing in the method below?"
 *
 * <pre>
 * &#64;Service
 * public class ProductService {
 *     public Product getProduct(Long id) {
 *         return repository.findById(id);
 *     }
 * }
 * </pre>
 *
 * The original had no repository field at all. Here one is injected through the constructor; what
 * is still missing is in {@link #getProduct}.
 */
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** Returns the product, or throws {@link ProductNotFoundException} if there is none. */
    public Product getProduct(long id) {
        return repository.findById(id).get();
    }
}
