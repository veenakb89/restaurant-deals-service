package com.eatclub.deals.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on Spring's caching (used for the upstream feed; settings in {@code application.properties}).
 * Kept here rather than on the application class so test slices such as {@code @WebMvcTest} don't enable it.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
