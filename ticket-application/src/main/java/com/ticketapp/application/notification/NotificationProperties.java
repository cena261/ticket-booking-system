package com.ticketapp.application.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private String from = "no-reply@ticketapp.local";
    private Duration timeout = Duration.ofSeconds(5);
    private Bulkhead bulkhead = new Bulkhead();

    @Getter
    @Setter
    public static class Bulkhead {
        private int maxConcurrentCalls = 8;
        private Duration maxWait = Duration.ZERO;
    }
}
