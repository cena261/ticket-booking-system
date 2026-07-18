package com.ticketapp.application.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReserveRateLimitProperties.class)
public class ReserveRateLimitConfig {
}
