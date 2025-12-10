package com.elcafe.modules.customer.repository;

import com.elcafe.modules.customer.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByCustomerIdAndActiveTrue(Long customerId);

    List<Address> findByCustomerId(Long customerId);

    Optional<Address> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Address> findByCustomerIdAndIsDefaultTrue(Long customerId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.customer.id = :customerId AND a.id != :addressId")
    void unsetDefaultForCustomer(Long customerId, Long addressId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.customer.id = :customerId")
    void unsetAllDefaultsForCustomer(Long customerId);

    boolean existsByIdAndCustomerId(Long id, Long customerId);
}
