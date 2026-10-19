package com.telemed.notifications.adapter.in.http;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter =
            new CorrelationIdFilter();

    @Test
    void shouldReuseValidIncomingCorrelationId()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(CorrelationContext.HEADER, "trace-abc.123");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        AtomicReference<String> traceIdDuringRequest =
                new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res
                    ) {
                        traceIdDuringRequest.set(
                                MDC.get(CorrelationContext.MDC_KEY)
                        );
                    }
                }
        );

        assertThat(response.getHeader(CorrelationContext.HEADER))
                .isEqualTo("trace-abc.123");
        assertThat(traceIdDuringRequest.get())
                .isEqualTo("trace-abc.123");
        assertThat(request.getAttribute(
                CorrelationContext.REQUEST_ATTRIBUTE
        )).isEqualTo("trace-abc.123");
        assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
    }

    @Test
    void shouldGenerateCorrelationIdWhenIncomingValueIsUnsafe()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.addHeader(CorrelationContext.HEADER, "bad value\r\n");
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated =
                response.getHeader(CorrelationContext.HEADER);

        assertThat(generated).isNotEqualTo("bad value\r\n");
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsMissing()
            throws Exception {
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                new MockFilterChain()
        );

        assertThat(UUID.fromString(
                response.getHeader(CorrelationContext.HEADER)
        )).isNotNull();
    }
}
