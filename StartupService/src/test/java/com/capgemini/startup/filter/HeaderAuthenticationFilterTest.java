package com.capgemini.startup.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeaderAuthenticationFilterTest {

    private HeaderAuthenticationFilter filter;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new HeaderAuthenticationFilter();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withGatewayHeaders_shouldSetAuthentication() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("42");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_FOUNDER");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("42");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).hasSize(1);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withMultipleRoles_shouldSetAllAuthorities() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("10");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_FOUNDER, ROLE_ADMIN");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).hasSize(2);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withNoHeaders_shouldNotSetAuthentication() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withOnlyUserId_shouldNotSetAuthentication() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("42");
        when(request.getHeader("X-User-Roles")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
