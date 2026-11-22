package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.order.OrderRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

/**
 * Unit tests: no Spring, no database. They only read the annotations on OrderRepository, so that
 * the two fixes of Task 6 really are two different techniques and not the same query twice.
 */
@Tag("task6")
class OrderRepositoryDeclarationsTest {

    private static final List<Method> METHODS = Arrays.asList(OrderRepository.class.getDeclaredMethods());

    @Test
    @DisplayName("JOIN FETCH: OrderRepository has a @Query that returns a List and says 'join fetch o.customer'")
    void hasAJoinFetchQuery() {
        List<String> queries = METHODS.stream()
                .filter(method -> method.isAnnotationPresent(Query.class))
                .filter(method -> method.getReturnType() == List.class)
                .map(method -> method.getAnnotation(Query.class).value().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))
                .toList();

        assertThat(queries).as("the @Query texts of the OrderRepository methods that return a List")
                .anyMatch(query -> query.matches(".*join fetch \\w+\\.customer.*"));
    }

    @Test
    @DisplayName("@EntityGraph: OrderRepository has a method with @EntityGraph(attributePaths = \"customer\") and no 'join fetch' of its own")
    void hasAnEntityGraphMethod() {
        List<Method> withGraph = METHODS.stream()
                .filter(method -> method.isAnnotationPresent(EntityGraph.class))
                .filter(method -> Arrays.asList(method.getAnnotation(EntityGraph.class).attributePaths()).contains("customer"))
                .filter(method -> !method.isAnnotationPresent(Query.class)
                        || !method.getAnnotation(Query.class).value().toLowerCase(Locale.ROOT).contains("join fetch"))
                .toList();

        assertThat(withGraph).as("OrderRepository methods with @EntityGraph(attributePaths = \"customer\")").isNotEmpty();
    }
}
