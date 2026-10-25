package com.telemed.notifications.app.config;

import com.telemed.notifications.adapter.out.identity.IdentityClientProperties;
import com.telemed.notifications.adapter.out.identity.IdentityPatientContactClient;
import com.telemed.notifications.application.port.out.PatientContactPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HU-17: the event consumers e-mail the patient at the address identity answers. Only
 * where they run, so the service client secret is required there and nowhere else.
 */
@Configuration
@ConditionalOnProperty(prefix = "notifications.messaging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdentityClientProperties.class)
public class IdentityClientConfiguration {

    @Bean
    PatientContactPort patientContact(IdentityClientProperties properties) {
        return new IdentityPatientContactClient(properties);
    }
}
