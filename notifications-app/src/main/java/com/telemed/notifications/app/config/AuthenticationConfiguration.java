package com.telemed.notifications.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.http.AuthenticationFilter;
import com.telemed.notifications.adapter.out.security.JwtAccessTokenVerifier;
import com.telemed.notifications.adapter.out.security.JwtProperties;
import com.telemed.notifications.adapter.out.security.RsaPublicKeyLoader;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Every /api/v1 request needs identity's access token; the service does not start without its public key. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthenticationConfiguration {

    @Bean
    AccessTokenVerifierPort accessTokenVerifier(JwtProperties properties) {
        return new JwtAccessTokenVerifier(
                RsaPublicKeyLoader.load(properties.publicKey(), properties.publicKeyFile()),
                properties.issuer()
        );
    }

    @Bean
    FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistration(
            AccessTokenVerifierPort verifier,
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<AuthenticationFilter> registration =
                new FilterRegistrationBean<>(new AuthenticationFilter(verifier, objectMapper));
        registration.addUrlPatterns("/api/v1/*");
        // Right after CorrelationIdFilter (HIGHEST_PRECEDENCE).
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
