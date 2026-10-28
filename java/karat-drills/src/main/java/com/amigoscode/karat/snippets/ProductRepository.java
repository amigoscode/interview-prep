package com.amigoscode.karat.snippets;

import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(long id);
}
