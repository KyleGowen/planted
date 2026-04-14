package com.planted.service;

import com.planted.entity.Plant;
import com.planted.entity.UserPhysicalAddress;
import com.planted.repository.UserPhysicalAddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPhysicalAddressService {

    private final UserPhysicalAddressRepository repository;

    @Value("${planted.user.default-id:default}")
    private String defaultUserId;

    @Transactional(readOnly = true)
    public Optional<String> getAddressForDefaultUser() {
        return getAddressForUserId(defaultUserId);
    }

    @Transactional(readOnly = true)
    public Optional<String> getAddressForUserId(String userId) {
        return repository.findById(userId)
                .map(UserPhysicalAddress::getAddress)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim);
    }

    /**
     * Address for LLM prompts: keyed by plant's user or the configured default tenant id.
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveAddressForPlant(Plant plant) {
        String key = (plant.getUserId() != null && !plant.getUserId().isBlank())
                ? plant.getUserId().trim()
                : defaultUserId;
        return getAddressForUserId(key);
    }

    @Transactional
    public void upsertForDefaultUser(String address) {
        upsertForUserId(defaultUserId, address);
    }

    @Transactional
    public void upsertForUserId(String userId, String address) {
        if (address == null || address.isBlank()) {
            repository.deleteById(userId);
            return;
        }
        String trimmed = address.trim();
        UserPhysicalAddress row = repository.findById(userId)
                .orElseGet(() -> UserPhysicalAddress.builder().userId(userId).build());
        row.setAddress(trimmed);
        repository.save(row);
    }

    public String getDefaultUserId() {
        return defaultUserId;
    }
}
