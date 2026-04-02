package de.market.publicapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class PublicApiSecurityConfig {

    private final PublicApiProperties properties;

    public PublicApiSecurityConfig(PublicApiProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicApiFilterChain(HttpSecurity http) throws Exception {
        var encoder = new BCryptPasswordEncoder();
        var user = User.builder()
                .username(properties.getUsername())
                .password(encoder.encode(properties.getPassword()))
                .roles("API")
                .build();
        var userDetailsService = new InMemoryUserDetailsManager(user);

        http
            .securityMatcher("/public-api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .userDetailsService(userDetailsService)
            .httpBasic(basic -> {});
        return http.build();
    }
}
