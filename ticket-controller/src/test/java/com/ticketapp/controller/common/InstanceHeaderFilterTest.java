package com.ticketapp.controller.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InstanceHeaderFilterTest {

    @Test
    void stampsTheConfiguredInstanceIdOnEveryResponse() throws Exception {
        InstanceHeaderFilter filter = new InstanceHeaderFilter("app-2");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader("X-Instance", "app-2");
        verify(chain).doFilter(request, response);
    }
}
