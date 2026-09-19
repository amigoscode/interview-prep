package com.amigoscode.ledgerhttp;

import com.amigoscode.ledger.Ledger;
import io.javalin.Javalin;

/** Story 1 starts here. Keep the HTTP layer thin; the rules live in the Ledger. */
public final class LedgerApp {

    private final Ledger ledger;

    public LedgerApp(Ledger ledger) {
        this.ledger = ledger;
    }

    /** Build the app without starting it, so tests can start it on any port. */
    public Javalin javalin() {
        return Javalin.create();
    }

    public static void main(String[] args) {
        new LedgerApp(new Ledger()).javalin().start(7070);
    }
}
