package com.org.ecommerce;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Authentication is session-token based (SessionTokenAuthenticationFilter); no default in-memory user.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class EcommerceApplication {

    public static void main(String[] args) {
        // All timestamps are handled in UTC regardless of host configuration.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(EcommerceApplication.class, args);
    }
}
