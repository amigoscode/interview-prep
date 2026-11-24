CREATE TABLE transactions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id     VARCHAR(255),
    reference       VARCHAR(255),
    amount          DOUBLE,
    fee             DOUBLE,
    currency        VARCHAR(255),
    captured_amount DOUBLE,
    refunded_amount DOUBLE,
    settled_amount  DOUBLE,
    status          INT,
    card_number     VARCHAR(255),
    card_holder     VARCHAR(255),
    cvv             VARCHAR(255),
    created_at      TIMESTAMP
);

CREATE TABLE refunds (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT,
    amount         DOUBLE,
    reason         VARCHAR(255),
    created_at     TIMESTAMP
);
