package com.amigoscode.transactional;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.transactional.bill.BillRejectedException;
import com.amigoscode.transactional.bill.BillService;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Unit tests: no Spring context, no database. They ask Spring's own annotation reader the question
 * the proxy asks when an exception leaves a method: "roll back, or commit?"
 */
@Tag("task4")
class RollbackRulesTest {

    /** A method with a plain @Transactional, to show the default rule. */
    static class PlainTransactional {

        @Transactional
        public void doWork() {
        }
    }

    @Test
    @DisplayName("Rollback rule: a plain @Transactional rolls back for a RuntimeException and for an Error")
    void defaultRuleRollsBackForUncheckedExceptions() throws Exception {
        TransactionAttribute rule = ruleFor(PlainTransactional.class, "doWork");

        assertThat(rule.rollbackOn(new IllegalStateException("unchecked"))).as("RuntimeException").isTrue();
        assertThat(rule.rollbackOn(new OutOfMemoryError("error"))).as("Error").isTrue();
    }

    @Test
    @DisplayName("Rollback rule: a plain @Transactional does NOT roll back for a checked exception: it commits")
    void defaultRuleCommitsForCheckedExceptions() throws Exception {
        TransactionAttribute rule = ruleFor(PlainTransactional.class, "doWork");

        assertThat(rule.rollbackOn(new IOException("checked"))).as("checked exception").isFalse();
        assertThat(rule.rollbackOn(new Exception("checked"))).as("java.lang.Exception itself").isFalse();
    }

    @Test
    @DisplayName("Rollback rule: BillService.payBill rolls back when BillRejectedException is thrown")
    void payBillRollsBackForBillRejectedException() throws Exception {
        TransactionAttribute rule = ruleFor(BillService.class, "payBill", long.class, long.class, long.class);

        assertThat(rule).as("payBill is @Transactional").isNotNull();
        assertThat(rule.rollbackOn(new BillRejectedException("rejected")))
                .as("roll back when payBill throws BillRejectedException").isTrue();
    }

    private static TransactionAttribute ruleFor(Class<?> type, String methodName, Class<?>... parameters) throws Exception {
        Method method = type.getMethod(methodName, parameters);
        return new AnnotationTransactionAttributeSource().getTransactionAttribute(method, type);
    }
}
