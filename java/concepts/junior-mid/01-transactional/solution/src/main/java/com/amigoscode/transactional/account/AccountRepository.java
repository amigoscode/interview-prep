package com.amigoscode.transactional.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    default Account require(long id) {
        return findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}
