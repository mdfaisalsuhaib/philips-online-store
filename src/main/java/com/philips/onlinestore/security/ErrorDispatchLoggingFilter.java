package com.philips.onlinestore.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ErrorDispatchLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ErrorDispatchLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() != DispatcherType.ERROR;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Object error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (error instanceof Throwable exception) {
            log.error(
                    "Unhandled exception during {} request to {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception
            );
        }
        filterChain.doFilter(request, response);
    }
}
