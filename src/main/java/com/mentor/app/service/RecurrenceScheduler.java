package com.mentor.app.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecurrenceScheduler {
    private final RecurrenceService service;

    // al arrancar, por si el servidor estuvo apagado
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        service.refresh();
    }

    // cada día, poco después de medianoche
    @Scheduled(cron = "0 5 0 * * *")
    public void daily() {
        service.refresh();
    }
}