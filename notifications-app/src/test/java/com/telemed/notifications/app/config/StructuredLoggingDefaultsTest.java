package com.telemed.notifications.app.config;

import com.telemed.notifications.app.observability.ContractLogFormatter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** Week 11 log contract (CP2): the console writes the contract's JSON lines, in every profile. */
class StructuredLoggingDefaultsTest {

    @Test
    void consoleUsesTheContractFormatter() throws IOException {
        Object format = null;
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            format = format != null ? format : source.getProperty("logging.structured.format.console");
        }

        assertThat(format).isEqualTo(ContractLogFormatter.class.getName());
    }
}
