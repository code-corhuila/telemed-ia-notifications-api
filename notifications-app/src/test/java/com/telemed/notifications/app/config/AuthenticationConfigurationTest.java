package com.telemed.notifications.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** NORMA: the service validates identity's RS256 tokens and does not start without the public key. */
class AuthenticationConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(AuthenticationConfiguration.class)
            .withPropertyValues("security.jwt.issuer=telemed-ia-identity-and-access");

    @Test
    void shouldNotStartWithoutThePublicKey() {

        context.run(started -> assertThat(started).hasFailed());
    }

    @Test
    void shouldVerifyTokensWithTheConfiguredPublicKey() throws Exception {

        context.withPropertyValues("security.jwt.public-key=" + publicKeyPem().replace("\n", "\\n"))
                .run(started -> assertThat(started)
                        .hasNotFailed()
                        .hasSingleBean(AccessTokenVerifierPort.class)
                        .hasBean("authenticationFilterRegistration")
                        .hasSingleBean(ObjectMapper.class));
    }

    @Test
    void shouldTrustIdentityAsIssuerByDefault() throws IOException {

        String issuer = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).get(0)
                .getProperty("security.jwt.issuer").toString();

        assertThat(issuer).isEqualTo("${JWT_ISSUER:telemed-ia-identity-and-access}");
    }

    private static String publicKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(generator.generateKeyPair().getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
