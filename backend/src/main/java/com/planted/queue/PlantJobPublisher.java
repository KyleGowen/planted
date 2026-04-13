package com.planted.queue;

/**
 * Abstraction over the job queue backend.
 * Local profile uses LocalPlantJobPublisher (inline/event-based).
 * Prod profile uses SqsPlantJobPublisher.
 */
public interface PlantJobPublisher {

    void publish(PlantJobMessage message);
}
