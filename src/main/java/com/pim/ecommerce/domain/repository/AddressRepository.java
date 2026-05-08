package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {
}