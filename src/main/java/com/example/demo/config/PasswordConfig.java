package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for password hashing.
 * 
 * Uses BCryptPasswordEncoder with a work factor (strength) of 12.
 * 
 * Rationale for Work Factor 12:
 * - BCrypt work factor 12 performs 2^12 = 4,096 hashing rounds.
 * - On modern server hardware, each hash takes ~100-250 milliseconds.
 * - This provides strong defense against offline brute-force, dictionary,
 *   and GPU/ASIC rainbow-table attacks while maintaining fast, responsive
 *   interactive login response times for human users.
 */
@Configuration
public class PasswordConfig {

    public static final int BCRYPT_WORK_FACTOR = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_WORK_FACTOR);
    }
}
