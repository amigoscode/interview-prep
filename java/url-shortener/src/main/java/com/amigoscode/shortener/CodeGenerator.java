package com.amigoscode.shortener;

/** Story 3. Produces a candidate short code; the shortener guarantees uniqueness. */
public interface CodeGenerator {
    String next();
}
