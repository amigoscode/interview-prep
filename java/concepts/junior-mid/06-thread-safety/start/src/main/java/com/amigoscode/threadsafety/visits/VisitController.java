package com.amigoscode.threadsafety.visits;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    public record Visits(int visits, int volatileVisits) {
    }

    private final VisitCounter counter;
    private final VolatileVisitCounter volatileCounter;

    public VisitController(VisitCounter counter, VolatileVisitCounter volatileCounter) {
        this.counter = counter;
        this.volatileCounter = volatileCounter;
    }

    @PostMapping
    public void visit() {
        counter.hit();
    }

    @PostMapping("/volatile")
    public void visitVolatile() {
        volatileCounter.hit();
    }

    @GetMapping
    public Visits visits() {
        return new Visits(counter.count(), volatileCounter.count());
    }
}
