package com.dropbox.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Enforce HTTPS
            .requiresChannel(channel -> 
                channel.requestMatchers(r -> r.getHeader("X-Forwarded-Proto") != null)
                       .requiresSecure())
            
            // Security headers
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentTypeOptions -> {})
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true))
                .addHeaderWriter((request, response) -> {
                    response.setHeader("X-Content-Type-Options", "nosniff");
                    response.setHeader("X-Frame-Options", "DENY");
                    response.setHeader("X-XSS-Protection", "1; mode=block");
                    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                    response.setHeader("Content-Security-Policy", 
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:");
                })
            )
            
            // Disable CSRF for REST API (but enable in production with proper token handling)
            .csrf(csrf -> csrf.disable())
            
            // Allow all requests for now (in production, implement proper authentication)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}