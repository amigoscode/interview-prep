package com.amigoscode.sqltransfer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void databaseIsWiredUp() {
        assertThat(db.balance("A")).isEqualByComparingTo("100.00");
        assertThat(db.balance("B")).isEqualByComparingTo("50.00");
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void naiveTransferMovesMoney() {
        transfers.transferNaive("A", "B", new BigDecimal("30.00"));
        assertThat(db.balance("A")).isEqualByComparingTo("70.00");
        assertThat(db.balance("B")).isEqualByComparingTo("80.00");
    }
}
