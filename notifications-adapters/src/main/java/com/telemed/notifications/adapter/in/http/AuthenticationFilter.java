package com.telemed.notifications.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.application.exception.AccessTokenVerificationException;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lets a request reach the API only with a valid identity access token and
 * exposes its user to the controllers. Registered for /api/v1/* after the
 * correlation filter, so 401 answers carry the traceId.
 */
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String USER_ATTRIBUTE = AuthenticationFilter.class.getName() + ".user";

    private final AccessTokenVerifierPort verifier;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(AccessTokenVerifierPort verifier, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    public static AuthenticatedUser currentUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(USER_ATTRIBUTE);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            unauthorized(request, response);
            return;
        }

        AuthenticatedUser user;
        try {
            user = verifier.verify(header.substring(BEARER.length()).trim());
        } catch (AccessTokenVerificationException exception) {
            unauthorized(request, response);
            return;
        }

        request.setAttribute(USER_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // One answer for every failure: the caller is not told why.
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                "UNAUTHORIZED",
                "A valid access token is required.",
                (String) request.getAttribute(CorrelationContext.REQUEST_ATTRIBUTE)
        ));
    }
}
