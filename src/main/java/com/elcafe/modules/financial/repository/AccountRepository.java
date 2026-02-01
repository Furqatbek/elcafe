package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByRestaurant_Id(Long restaurantId);

    List<Account> findByRestaurant_Id(Long restaurantId);

    List<Account> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    List<Account> findByRestaurant_IdAndType(Long restaurantId, Account.AccountType type);

    List<Account> findByRestaurant_IdAndCategory(Long restaurantId, Account.AccountCategory category);

    Optional<Account> findByRestaurant_IdAndCode(Long restaurantId, String code);

    Optional<Account> findByRestaurant_IdAndName(Long restaurantId, String name);

    List<Account> findByRestaurant_IdAndParentAccount_Id(Long restaurantId, Long parentAccountId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.restaurant.id = :restaurantId AND a.systemAccount = true")
    List<Account> findSystemAccounts(Long restaurantId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.restaurant.id = :restaurantId AND a.parentAccount IS NULL AND a.active = true")
    List<Account> findRootAccounts(Long restaurantId);

    /**
     * Find account by ID with pessimistic write lock.
     * Use this method when updating account balances to prevent race conditions
     * in concurrent transactions (e.g., parallel journal entries).
     *
     * @param id the account ID
     * @return the account with an exclusive lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FinancialAccount a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(Long id);
}
