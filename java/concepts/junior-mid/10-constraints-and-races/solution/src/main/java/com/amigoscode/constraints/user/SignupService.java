package com.amigoscode.constraints.user;

import com.amigoscode.constraints.db.Violations;
import com.amigoscode.constraints.work.SlowWork;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Deliberately NOT transactional. users.save runs in its own short transaction, so when the
 * database refuses the row that transaction is already over by the time the exception is caught
 * here. Task 6 shows what goes wrong when you catch it inside the transaction instead.
 */
@Service
public class SignupService {

    /** The names of the two unique rules on users.email, as written in schema.sql. */
    public static final String EMAIL_UNIQUE = "users_email_key";
    public static final String EMAIL_UNIQUE_ANY_CASE = "users_email_lower_key";

    private final UserRepository users;
    private final SlowWork work;

    public SignupService(UserRepository users, SlowWork work) {
        this.users = users;
        this.work = work;
    }

    public User signUp(String email, String displayName) {
        // The fast path. It answers almost every duplicate with a cheap read and a clear message.
        // It cannot be trusted: two requests can both get "false" here.
        if (users.existsByEmailIgnoreCase(email)) {               // CHECK
            throw new EmailTakenException(email);
        }
        work.run("hash the password");                            // ...time passes...
        try {
            return users.save(new User(email, displayName));      // INSERT
        } catch (DataIntegrityViolationException e) {
            // The last line of defence. The database said no: find out which rule it was.
            if (Violations.isOn(e, EMAIL_UNIQUE, EMAIL_UNIQUE_ANY_CASE)) {
                throw new EmailTakenException(email);
            }
            throw e;
        }
    }
}
