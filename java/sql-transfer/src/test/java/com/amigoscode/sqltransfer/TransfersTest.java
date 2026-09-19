package com.amigoscode.sqltransfer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Single-session behaviour of every variant. The anomalies are in AnomaliesTest. */
class TransfersTest {

    Database db;
    Transfers transfers;

    @BeforeEach
    void setUp() {
        db = new Database();
        db.createAccount("A", new BigDecimal("100.00"));
        db.createAccount("B", new BigDecimal("50.00"));
        transfers = new Transfers(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Nested
    class Story1Naive {
        @Test void movesMoney() {
            transfers.transferNaive("A", "B", new BigDecimal("30.00"));
            assertThat(db.balance("A")).isEqualByComparingTo("70.00");
            assertThat(db.balance("B")).isEqualByComparingTo("80.00");
        }
        @Test void insufficientFundsRollsBackEverything() {
            assertThatThrownBy(() -> transfers.transferNaive("B", "A", new BigDecimal("60.00")))
                .isInstanceOf(InsufficientFundsException.class);
            assertThat(db.balance("A")).isEqualByComparingTo("100.00");
            assertThat(db.balance("B")).isEqualByComparingTo("50.00");
        }
        @Test void unknownAccountRollsBack() {
            assertThatThrownBy(() -> transfers.transferNaive("A", "X", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(db.balance("A")).isEqualByComparingTo("100.00");
        }
        @Test void nonPositiveAmountIsRejected() {
            assertThatThrownBy(() -> transfers.transferNaive("A", "B", BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Story3Pessimistic {
        @Test void movesMoney() {
            transfers.transferPessimistic("A", "B", new BigDecimal("30.00"));
            assertThat(db.balance("A")).isEqualByComparingTo("70.00");
            assertThat(db.balance("B")).isEqualByComparingTo("80.00");
        }
        @Test void insufficientFundsRollsBackEverything() {
            assertThatThrownBy(() -> transfers.transferPessimistic("B", "A", new BigDecimal("60.00")))
                .isInstanceOf(InsufficientFundsException.class);
            assertThat(db.balance("B")).isEqualByComparingTo("50.00");
        }
    }

    @Nested
    class Story4Optimistic {
        @Test void movesMoneyAndBumpsVersions() throws Exception {
            transfers.transferOptimistic("A", "B", new BigDecimal("30.00"));
            assertThat(db.balance("A")).isEqualByComparingTo("70.00");
            try (var c = db.connect()) {
                assertThat(Transfers.readVersioned(c, "A").version()).isEqualTo(1);
                assertThat(Transfers.readVersioned(c, "B").version()).isEqualTo(1);
            }
        }
        @Test void insufficientFundsRollsBackEverything() {
            assertThatThrownBy(() -> transfers.transferOptimistic("B", "A", new BigDecimal("60.00")))
                .isInstanceOf(InsufficientFundsException.class);
            assertThat(db.balance("B")).isEqualByComparingTo("50.00");
        }
    }

    @Nested
    class Story5OneStatement {
        @Test void movesMoney() {
            transfers.transferOneStatement("A", "B", new BigDecimal("30.00"));
            assertThat(db.balance("A")).isEqualByComparingTo("70.00");
            assertThat(db.balance("B")).isEqualByComparingTo("80.00");
        }
        @Test void insufficientFundsTouchesNothing() {
            assertThatThrownBy(() -> transfers.transferOneStatement("B", "A", new BigDecimal("60.00")))
                .isInstanceOf(InsufficientFundsException.class);
            assertThat(db.balance("B")).isEqualByComparingTo("50.00");
        }
    }
}
