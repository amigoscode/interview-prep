package com.amigoscode.ledgerhttp;

import com.amigoscode.ledger.AccountNotFoundException;
import com.amigoscode.ledger.InsufficientFundsException;
import com.amigoscode.ledger.Ledger;
import com.amigoscode.ledger.TransferReceipt;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The ledger behind HTTP, kept deliberately thin: parse, call the ledger, map the outcome to
 * a status code. Every rule about money lives in {@link Ledger}; if a rule shows up here it is
 * in the wrong place. Exceptions map to statuses in one spot so no handler repeats it.
 */
public final class LedgerApp {

    private final Ledger ledger;

    public LedgerApp(Ledger ledger) {
        this.ledger = ledger;
    }

    /** Build the app without starting it, so tests can start it on any port. */
    public Javalin javalin() {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);

        app.post("/accounts", this::openAccount);
        app.get("/accounts/{id}", this::getAccount);
        app.post("/transfers", this::transfer);

        app.exception(AccountNotFoundException.class, (e, ctx) -> error(ctx, HttpStatus.NOT_FOUND, e));
        app.exception(InsufficientFundsException.class, (e, ctx) -> error(ctx, HttpStatus.UNPROCESSABLE_CONTENT, e));
        app.exception(IllegalArgumentException.class, (e, ctx) -> error(ctx, HttpStatus.BAD_REQUEST, e));
        app.exception(NumberFormatException.class, (e, ctx) -> error(ctx, HttpStatus.BAD_REQUEST, e));
        return app;
    }

    public record OpenAccountRequest(String id, String openingBalance) { }
    public record TransferRequest(String from, String to, String amount) { }
    public record AccountResponse(String id, String balance) { }

    private void openAccount(Context ctx) {
        OpenAccountRequest req = ctx.bodyAsClass(OpenAccountRequest.class);
        ledger.open(req.id(), money(req.openingBalance()));
        ctx.status(HttpStatus.CREATED).json(new AccountResponse(req.id(), ledger.balance(req.id()).toPlainString()));
    }

    private void getAccount(Context ctx) {
        String id = ctx.pathParam("id");
        ctx.json(new AccountResponse(id, ledger.balance(id).toPlainString()));
    }

    /**
     * Story 4: an Idempotency-Key header routes through the ledger's keyed transfer, so a
     * retried request (a client that timed out and sent again) cannot move money twice. In
     * production that key-to-receipt map lives in Postgres or Redis with a TTL, not in memory.
     */
    private void transfer(Context ctx) {
        TransferRequest req = ctx.bodyAsClass(TransferRequest.class);
        String key = ctx.header("Idempotency-Key");
        if (key == null || key.isBlank()) {
            ledger.transfer(req.from(), req.to(), money(req.amount()));
            ctx.json(Map.of("status", "ok"));
            return;
        }
        TransferReceipt receipt = ledger.transfer(key, req.from(), req.to(), money(req.amount()));
        if (receipt.succeeded()) {
            ctx.json(Map.of("status", "ok", "idempotencyKey", key));
        } else {
            ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).json(Map.of("error", receipt.reason(), "idempotencyKey", key));
        }
    }

    private static BigDecimal money(String value) {
        if (value == null) throw new IllegalArgumentException("amount is required");
        return new BigDecimal(value);
    }

    private static void error(Context ctx, HttpStatus status, Exception e) {
        ctx.status(status).json(Map.of("error", e.getMessage() == null ? status.getMessage() : e.getMessage()));
    }

    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        ledger.open("A", new BigDecimal("100.00"));
        ledger.open("B", new BigDecimal("50.00"));
        new LedgerApp(ledger).javalin().start(7070);
        System.out.println("Try: curl -s localhost:7070/accounts/A");
    }
}
