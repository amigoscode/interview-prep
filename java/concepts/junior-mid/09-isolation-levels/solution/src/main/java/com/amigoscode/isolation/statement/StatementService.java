package com.amigoscode.isolation.statement;

import com.amigoscode.isolation.account.AccountRepository;
import com.amigoscode.isolation.checkpoint.Checkpoint;
import com.amigoscode.isolation.statement.Statement.Line;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {

    private final AccountRepository accounts;
    private final Checkpoint checkpoint;

    public StatementService(AccountRepository accounts, Checkpoint checkpoint) {
        this.accounts = accounts;
        this.checkpoint = checkpoint;
    }

    /**
     * Three queries, one transaction. At REPEATABLE READ the transaction takes one snapshot at its
     * first query and every later query reads from that same snapshot, so the lines, the count and
     * the total always describe the same moment, whatever other sessions commit in between.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Statement statement(String owner) {
        List<Line> lines = accounts.findByOwnerOrderById(owner).stream()      // QUERY 1: the lines
                .map(account -> new Line(account.getName(), account.getBalance()))
                .toList();
        checkpoint.reached(Checkpoint.LINES_READ);                            // ...time passes...
        long accountCount = accounts.countByOwner(owner);                     // QUERY 2: how many accounts
        long total = accounts.totalBalance(owner);                            // QUERY 3: the total
        return new Statement(owner, lines, accountCount, total);
    }
}
