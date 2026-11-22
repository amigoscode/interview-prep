package com.amigoscode.transactional;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.transactional.exhibit.VisibilityCaller;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.transfer.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;

/** Task 3. These tests pass from the start: they pin down how Spring's proxy behaves. */
@Tag("task3")
class ProxyIT extends IntegrationTest {

    @Autowired
    TransferService transferService;

    @Autowired
    VisibilityCaller caller;

    @Test
    @DisplayName("Proxy: the TransferService that Spring injects is a generated subclass of your class, not your class")
    void theInjectedBeanIsAProxy() {
        assertThat(transferService.getClass()).isNotEqualTo(TransferService.class);
        assertThat(transferService).isInstanceOf(TransferService.class);
        assertThat(AopUtils.isCglibProxy(transferService)).as("is a CGLIB proxy").isTrue();
        assertThat(AopUtils.getTargetClass(transferService)).isEqualTo(TransferService.class);
    }

    @Test
    @DisplayName("Proxy: a call from another bean opens a transaction, the same annotation reached through this.method() does not")
    void onlyCallsThroughTheProxyOpenATransaction() {
        assertThat(caller.callPublic().transactionActive()).as("called from another bean").isTrue();
        assertThat(caller.callPrivate().transactionActive()).as("called as this.privateMethod()").isFalse();
    }
}
