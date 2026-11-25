package com.amigoscode.isolation.statement;

import com.amigoscode.isolation.account.AccountRepository;
import com.amigoscode.isolation.checkpoint.Checkpoint;
import com.amigoscode.isolation.statement.Statement.Line;
import java.util.List;
import org.springframework.stereotype.Service;
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
     * Three queries, one transaction. THE BUG IS HERE: at the default isolation level every query
     * sees whatever is committed at the moment that query starts, so the lines, the count and the
     * total can each describe a different moment.
     *
     * <p>TODO Task 2: make all three queries read from one snapshot.
     */
    @Transactional(readOnly = true)
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
