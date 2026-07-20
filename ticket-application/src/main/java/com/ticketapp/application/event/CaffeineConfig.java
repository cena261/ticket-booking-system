package com.ticketapp.application.event;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EventCacheProperties.class)
public class CaffeineConfig {

    @Bean
    public Cache<Long, CachedEventMetadata> eventMetadataL1Cache(EventCacheProperties properties) {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaximumSize())
                .expireAfterWrite(properties.getL1ExpireAfterWrite())
                .build();
    }
}
