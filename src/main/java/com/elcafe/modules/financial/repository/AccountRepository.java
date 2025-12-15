package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByRestaurantId(Long restaurantId);

    List<Account> findByRestaurantIdAndActiveTrue(Long restaurantId);

    List<Account> findByRestaurantIdAndType(Long restaurantId, Account.AccountType type);

    List<Account> findByRestaurantIdAndCategory(Long restaurantId, Account.AccountCategory category);

    Optional<Account> findByRestaurantIdAndCode(Long restaurantId, String code);

    Optional<Account> findByRestaurantIdAndName(Long restaurantId, String name);

    List<Account> findByRestaurantIdAndParentAccountId(Long restaurantId, Long parentAccountId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.restaurant.id = :restaurantId AND a.systemAccount = true")
    List<Account> findSystemAccounts(Long restaurantId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.restaurant.id = :restaurantId AND a.parentAccount IS NULL AND a.active = true")
    List<Account> findRootAccounts(Long restaurantId);
}
