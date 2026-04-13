package com.planted.queue;

import org.springframework.context.ApplicationEvent;

public class PlantJobEvent extends ApplicationEvent {

    private final PlantJobMessage message;

    public PlantJobEvent(Object source, PlantJobMessage message) {
        super(source);
        this.message = message;
    }

    public PlantJobMessage getMessage() {
        return message;
    }
}
