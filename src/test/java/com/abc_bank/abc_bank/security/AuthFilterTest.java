package com.abc_bank.abc_bank.security;

import com.abc_bank.abc_bank.exceptions.CustomAuthenticationEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Mock
    private CustomerUserDetailsService customerUserDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private AuthFilter authFilter;

    @BeforeEach
    void clearContextBefore() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContextAfter() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_passesThroughWhenAuthorizationHeaderIsMissing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        authFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verify(customAuthenticationEntryPoint, never()).commence(any(), any(), any());
    }

    @Test
    void doFilter_passesThroughWhenHeaderIsNotBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic abc123");

        authFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_setsAuthenticationForValidToken() throws Exception {
        String token = "valid.jwt.token";
        String email = "alice@example.com";
        UserDetails userDetails = new User(email, "x", Collections.emptyList());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.getUsernameFromToken(token)).thenReturn(email);
        when(customerUserDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(tokenService.isTokenValid(token, userDetails)).thenReturn(true);

        authFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_doesNotAuthenticateWhenTokenIsInvalid() throws Exception {
        String token = "stale.jwt.token";
        String email = "bob@example.com";
        UserDetails userDetails = new User(email, "x", Collections.emptyList());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.getUsernameFromToken(token)).thenReturn(email);
        when(customerUserDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(tokenService.isTokenValid(token, userDetails)).thenReturn(false);

        authFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invokesEntryPointAndShortCircuitsWhenTokenParsingFails() throws Exception {
        String token = "bogus";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.getUsernameFromToken(token))
                .thenThrow(new RuntimeException("malformed"));

        authFilter.doFilter(request, response, filterChain);

        verify(customAuthenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
