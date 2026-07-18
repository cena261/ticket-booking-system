package com.ticketapp.controller.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private static final String INSTANCE_HEADER = "X-Instance";

    private final String instanceId;

    public InstanceHeaderFilter(@Value("${INSTANCE_ID:app-1}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(INSTANCE_HEADER, instanceId);
        filterChain.doFilter(request, response);
    }
}
