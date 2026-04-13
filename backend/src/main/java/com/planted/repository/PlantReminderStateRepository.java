package com.planted.repository;

import com.planted.entity.PlantReminderState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlantReminderStateRepository extends JpaRepository<PlantReminderState, Long> {

    Optional<PlantReminderState> findByPlantId(Long plantId);
}
