package com.planted.service;

import com.planted.entity.Plant;
import com.planted.entity.UserPhysicalAddress;
import com.planted.repository.UserPhysicalAddressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPhysicalAddressServiceTest {

    @Mock
    private UserPhysicalAddressRepository repository;

    private UserPhysicalAddressService service;

    @BeforeEach
    void setUp() {
        service = new UserPhysicalAddressService(repository);
        ReflectionTestUtils.setField(service, "defaultUserId", "default");
    }

    @Test
    void resolveAddressForPlant_usesPlantUserIdWhenSet() {
        when(repository.findById("alice")).thenReturn(Optional.of(
                UserPhysicalAddress.builder().userId("alice").address("  Paris  ").build()));

        Plant plant = Plant.builder().userId("alice").build();

        assertThat(service.resolveAddressForPlant(plant)).contains("Paris");
    }

    @Test
    void resolveAddressForPlant_fallsBackToDefaultUserId() {
        when(repository.findById("default")).thenReturn(Optional.of(
                UserPhysicalAddress.builder().userId("default").address("Portland, OR").build()));

        Plant plant = Plant.builder().userId(null).build();

        assertThat(service.resolveAddressForPlant(plant)).contains("Portland, OR");
    }

    @Test
    void upsertForDefaultUser_blankDeletes() {
        service.upsertForDefaultUser("  ");
        verify(repository).deleteById("default");
        verify(repository, never()).save(any());
    }

    @Test
    void upsertForDefaultUser_trimsAndSaves() {
        when(repository.findById("default")).thenReturn(Optional.empty());
        service.upsertForDefaultUser("  Berlin  ");

        ArgumentCaptor<UserPhysicalAddress> cap = ArgumentCaptor.forClass(UserPhysicalAddress.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getAddress()).isEqualTo("Berlin");
        assertThat(cap.getValue().getUserId()).isEqualTo("default");
    }
}
