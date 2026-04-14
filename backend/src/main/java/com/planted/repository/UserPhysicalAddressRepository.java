package com.planted.repository;

import com.planted.entity.UserPhysicalAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPhysicalAddressRepository extends JpaRepository<UserPhysicalAddress, String> {
}
