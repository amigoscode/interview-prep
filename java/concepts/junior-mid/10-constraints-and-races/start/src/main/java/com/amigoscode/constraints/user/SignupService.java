package com.amigoscode.constraints.user;

import com.amigoscode.constraints.work.SlowWork;
import org.springframework.stereotype.Service;

/**
 * Deliberately NOT transactional. users.save runs in its own short transaction, so when the
 * database refuses a row that transaction is already over by the time the exception arrives here.
 * Task 6 shows what goes wrong when you catch it inside the transaction instead.
 */
@Service
public class SignupService {

    private final UserRepository users;
    private final SlowWork work;

    public SignupService(UserRepository users, SlowWork work) {
        this.users = users;
        this.work = work;
    }

    /**
     * THE BUG IS HERE. Check, then insert: nothing ties the two together.
     *
     * <p>TODO Task 2: keep the check. Make the database refuse the second row (schema.sql), and
     * turn that refusal into an EmailTakenException.
     *
     * <p>TODO Task 4: Ana@Example.com and ana@example.com are the same address.
     */
    public User signUp(String email, String displayName) {
        if (users.existsByEmail(email)) {                         // CHECK
            throw new EmailTakenException(email);
        }
        work.run("hash the password");                            // ...time passes...
        return users.save(new User(email, displayName));          // INSERT
    }
}
