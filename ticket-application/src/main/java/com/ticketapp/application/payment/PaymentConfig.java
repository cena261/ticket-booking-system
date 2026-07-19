package com.ticketapp.application.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SepayProperties.class)
public class PaymentConfig {
}
