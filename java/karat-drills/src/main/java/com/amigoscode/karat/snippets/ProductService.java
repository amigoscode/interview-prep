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
 * Three things: the repository is never declared or injected (constructor injection makes it
 * explicit and testable with a fake, as in the test); {@code findById} returns an
 * {@code Optional<Product>}, not a {@code Product}, so it does not compile; and the not-found case
 * has no decision. Here the decision is a domain exception that a web layer maps to 404.
 */
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /** Returns the product, or throws {@link ProductNotFoundException} if there is none. */
    public Product getProduct(long id) {
        // Bug was Optional.get(): a missing product surfaced as NoSuchElementException with no id in it.
        return repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
