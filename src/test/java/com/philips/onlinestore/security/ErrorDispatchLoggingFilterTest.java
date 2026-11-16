package com.philips.onlinestore.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ErrorDispatchLoggingFilterTest {

    @Test
    void logsOriginalErrorDispatchExceptionAndStackTrace(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(
                RequestDispatcher.ERROR_EXCEPTION,
                new IllegalStateException("diagnostic failure")
        );

        new ErrorDispatchLoggingFilter().doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertTrue(output.getAll().contains("Unhandled exception during POST request to /error"));
        assertTrue(output.getAll().contains("java.lang.IllegalStateException: diagnostic failure"));
        assertTrue(output.getAll().contains("ErrorDispatchLoggingFilterTest"));
    }
}
