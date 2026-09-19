package com.amigoscode.lb;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomSelection implements SelectionStrategy {
    @Override
    public String select(List<String> addresses) {
        return addresses.get(ThreadLocalRandom.current().nextInt(addresses.size()));
    }
}
