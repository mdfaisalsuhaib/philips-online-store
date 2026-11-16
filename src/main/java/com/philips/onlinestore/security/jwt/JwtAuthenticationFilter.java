package com.philips.onlinestore.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.springframework.security.core.context.SecurityContextHolder.createEmptyContext;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.core.context.SecurityContextHolder.setContext;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader("Authorization");
        boolean authorizationHeaderPresent = authorizationHeader != null;
        boolean bearerPrefixDetected = authorizationHeaderPresent
                && authorizationHeader.startsWith(BEARER_PREFIX);
        log.info(
                "JWT diagnostics: Authorization header present={}, Bearer prefix detected={}",
                authorizationHeaderPresent,
                bearerPrefixDetected
        );

        if (!bearerPrefixDetected) {
            log.info("JWT diagnostics: authentication set in SecurityContext by JWT filter=false");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!jwtService.isTokenValid(token)) {
            log.info("JWT diagnostics: token validation=failed");
            log.info("JWT diagnostics: authentication set in SecurityContext by JWT filter=false");
            reject(request, response);
            return;
        }
        log.info("JWT diagnostics: token validation=succeeded");

        String subject = jwtService.extractSubject(token);
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(subject);
        } catch (UsernameNotFoundException exception) {
            log.info("JWT diagnostics: subject user found in database=false");
            log.info("JWT diagnostics: authentication set in SecurityContext by JWT filter=false");
            reject(request, response);
            return;
        }
        log.info("JWT diagnostics: subject user found in database=true");

        if (!userDetails.isEnabled()) {
            log.info("JWT diagnostics: authentication set in SecurityContext by JWT filter=false");
            reject(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        var securityContext = createEmptyContext();
        securityContext.setAuthentication(authentication);
        setContext(securityContext);
        log.info("JWT diagnostics: authentication set in SecurityContext by JWT filter=true");

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        getContext().setAuthentication(null);
        authenticationEntryPoint.commence(
                request,
                response,
                new org.springframework.security.authentication.BadCredentialsException("Invalid bearer token.")
        );
    }
}
