package com.amigoscode.isolation.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByOwnerOrderById(String owner);

    long countByOwner(String owner);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.owner = :owner")
    long totalBalance(@Param("owner") String owner);
}
