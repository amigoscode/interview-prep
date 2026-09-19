package com.amigoscode.lb;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalancerTest {

    @Test
    void projectIsWiredUp() {
        assertThat(LoadBalancer.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void registersAnAddress() {
        LoadBalancer lb = new LoadBalancer(10);
        assertThat(lb.register("10.0.0.1")).isTrue();
    }
}
