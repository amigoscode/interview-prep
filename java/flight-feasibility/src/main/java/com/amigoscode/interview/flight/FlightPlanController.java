package com.amigoscode.interview.flight;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flights")
public class FlightPlanController {

    @PostMapping("/feasibility")
    public FeasibilityResponse assess(@RequestBody FlightPlanRequest request) {
        System.out.println("Assessing flight " + request.flightNumber());

        // TODO: work out the distance, evaluate the rules from the brief, and return the verdict.
        throw new UnsupportedOperationException("Not implemented yet — see INTERVIEW_TASKS.md");
    }

}
