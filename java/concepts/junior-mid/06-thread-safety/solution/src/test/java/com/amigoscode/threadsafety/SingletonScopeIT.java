package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.support.IntegrationTest;
import com.amigoscode.threadsafety.support.Race;
import com.amigoscode.threadsafety.visits.VisitCounter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tasks 1 and 7. These tests pass on purpose: they pin down how Spring scopes work, which is the
 * cause of every bug in this project. Real HTTP requests against the real application.
 */
class SingletonScopeIT extends IntegrationTest {

    private static final int REQUESTS = 8;

    @Test
    @Tag("task1")
    @DisplayName("Scope: Spring builds one VisitCounter and hands the same object to everyone who asks")
    void theContainerHandsOutTheSameObject() {
        VisitCounter first = context.getBean(VisitCounter.class);
        VisitCounter second = context.getBean(VisitCounter.class);

        assertThat(second).isSameAs(first);
        assertThat(context.isSingleton("visitCounter")).isTrue();
    }

    @Test
    @Tag("task1")
    @DisplayName("Scope: 8 requests at the same moment are served by ONE controller and ONE VisitCounter (this is why fields are shared)")
    void everyRequestIsServedByTheSameObjects() {
        Answers answers = askWhoAmI();

        assertThat(answers.of("controller")).as("controller instances").hasSize(1);
        assertThat(answers.of("singletonService")).as("VisitCounter instances").hasSize(1);
    }

    @Test
    @Tag("task7")
    @DisplayName("Scope: a request-scoped bean is a new object for every request; a prototype injected into a singleton is created once")
    void requestScopeIsPerRequestAndAnInjectedPrototypeIsNot() {
        Answers answers = askWhoAmI();

        assertThat(answers.of("requestScopedBean")).as("RequestInfo instances").hasSize(REQUESTS);
        assertThat(answers.of("prototypeBean")).as("PrototypeHelper instances seen by the singleton controller").hasSize(1);
    }

    /** Sends 8 GET /api/whoami requests at the same moment and collects the answers, field by field. */
    private Answers askWhoAmI() {
        Set<String> bodies = ConcurrentHashMap.newKeySet();
        List<Throwable> thrown = Race.run(REQUESTS, 1, (thread, i) -> bodies.add(get("/api/whoami?holdMillis=50").body()));
        assertThat(Race.describe(thrown)).isEmpty();
        assertThat(bodies).as("answers").hasSize(REQUESTS);
        return new Answers(bodies);
    }

    private record Answers(Set<String> bodies) {

        Set<String> of(String field) {
            Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
            Set<String> values = ConcurrentHashMap.newKeySet();
            for (String body : bodies) {
                Matcher matcher = pattern.matcher(body);
                assertThat(matcher.find()).as("field %s in %s", field, body).isTrue();
                values.add(matcher.group(1));
            }
            return values;
        }
    }
}
