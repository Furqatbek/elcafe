package com.elcafe.modules.financial.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Account.AccountCategory;
import com.elcafe.modules.financial.entity.Account.AccountType;
import com.elcafe.modules.financial.entity.Account.NormalBalance;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AccountRepositoryTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private Account createAccount(String code, String name, boolean systemAccount, boolean active, Account parentAccount) {
        Account account = Account.builder()
                .restaurant(restaurant)
                .code(code)
                .name(name)
                .type(AccountType.ASSET)
                .category(AccountCategory.CASH)
                .balance(BigDecimal.ZERO)
                .normalBalance(NormalBalance.DEBIT)
                .active(active)
                .systemAccount(systemAccount)
                .parentAccount(parentAccount)
                .build();
        em.persist(account);
        return account;
    }

    @Test
    @DisplayName("findSystemAccounts returns only accounts where systemAccount=true")
    void findSystemAccounts() {
        createAccount("1000", "Cash", true, true, null);
        createAccount("1001", "Bank", true, true, null);
        createAccount("2000", "Custom Account", false, true, null);

        em.flush();
        em.clear();

        List<Account> systemAccounts = accountRepository.findSystemAccounts(restaurant.getId());
        assertEquals(2, systemAccounts.size());
        assertTrue(systemAccounts.stream().allMatch(Account::getSystemAccount));
    }

    @Test
    @DisplayName("findRootAccounts returns active accounts with no parent")
    void findRootAccounts() {
        Account root1 = createAccount("1000", "Cash", false, true, null);
        createAccount("1001", "Petty Cash", false, true, root1); // has parent
        createAccount("2000", "Revenue", false, true, null); // root, active
        createAccount("3000", "Inactive", false, false, null); // inactive, should not appear

        em.flush();
        em.clear();

        List<Account> roots = accountRepository.findRootAccounts(restaurant.getId());
        assertEquals(2, roots.size());
        assertTrue(roots.stream().allMatch(a -> a.getActive() && a.getParentAccountId() == null));
    }
}
