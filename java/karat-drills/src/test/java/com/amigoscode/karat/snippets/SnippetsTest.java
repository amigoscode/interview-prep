package com.amigoscode.karat.snippets;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Five snippets from the theory half of the interview. Every test fails until its snippet is fixed. */
class SnippetsTest {

    @Test
    void booleanCheckTreatsNullAsFalse() {
        assertThat(BooleanCheck.isValid(Boolean.TRUE)).isTrue();
        assertThat(BooleanCheck.isValid(Boolean.FALSE)).isFalse();
        assertThat(BooleanCheck.isValid(null)).isFalse();
    }

    @Test
    void joiningDateCannotBeChangedFromOutside() {
        Date joined = new Date(1_000_000L);
        Employee employee = new Employee(joined);

        employee.getJoiningDate().setTime(0);
        joined.setTime(5);

        assertThat(employee.getJoiningDate()).isEqualTo(new Date(1_000_000L));
    }

    @Test
    void sortsByLengthWithoutChangingTheInput() {
        List<String> words = new ArrayList<>(List.of("Java", "Spring", "API", "Microservices"));

        List<String> sorted = WordSorter.sortedByLength(words);

        assertThat(sorted).containsExactly("API", "Java", "Spring", "Microservices");
        assertThat(words).containsExactly("Java", "Spring", "API", "Microservices");
    }

    @Test
    void wordsOfEqualLengthKeepTheirOrder() {
        assertThat(WordSorter.sortedByLength(List.of("bb", "aa", "c", "dd")))
                .containsExactly("c", "bb", "aa", "dd");
        assertThat(WordSorter.sortedByLength(List.of())).isEmpty();
    }

    @Test
    void renamedUserIsStillFoundInTheDirectory() {
        UserDirectory directory = new UserDirectory();
        directory.add(new User("alice"));

        assertThat(directory.rename("alice", "bob")).isTrue();

        assertThat(directory.contains("bob")).isTrue();
        assertThat(directory.contains("alice")).isFalse();
        assertThat(directory.size()).isEqualTo(1);
    }

    @Test
    void renamingAnUnknownUserChangesNothing() {
        UserDirectory directory = new UserDirectory();
        directory.add(new User("alice"));

        assertThat(directory.rename("carol", "bob")).isFalse();
        assertThat(directory.contains("alice")).isTrue();
        assertThat(directory.size()).isEqualTo(1);
    }

    @Test
    void userIsAValueObject() {
        Set<User> users = new HashSet<>(List.of(new User("alice"), new User("alice")));

        assertThat(users).hasSize(1);
        assertThat(new User("alice").withUsername("bob")).isEqualTo(new User("bob"));
    }

    @Test
    void missingProductIsReportedByName() {
        ProductRepository repository = id -> id == 1 ? Optional.of(new Product(1, "Keyboard")) : Optional.empty();
        ProductService service = new ProductService(repository);

        assertThat(service.getProduct(1)).isEqualTo(new Product(1, "Keyboard"));
        assertThatThrownBy(() -> service.getProduct(2))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product 2 not found");
    }
}
