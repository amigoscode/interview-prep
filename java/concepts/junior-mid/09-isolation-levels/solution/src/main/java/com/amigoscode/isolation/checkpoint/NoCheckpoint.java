package com.amigoscode.isolation.checkpoint;

import org.springframework.stereotype.Component;

/** What runs in production: nothing. */
@Component
public class NoCheckpoint implements Checkpoint {

    @Override
    public void reached(String name) {
    }
}
