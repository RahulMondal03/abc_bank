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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock private TokenService tokenService;
    @Mock private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @Mock private CustomerUserDetailsService customerUserDetailsService;
    @Mock private FilterChain filterChain;

    @InjectMocks private AuthFilter authFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_whenNoAuthHeader_chainContinuesWithoutContext() throws Exception {
        authFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tokenService, never()).getUsernameFromToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_whenHeaderMissingBearerPrefix_treatsAsNoToken() throws Exception {
        request.addHeader("Authorization", "token-without-bearer");

        authFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tokenService, never()).getUsernameFromToken(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_whenTokenParsingThrows_invokesEntryPointAndDoesNotContinueChain() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        when(tokenService.getUsernameFromToken("bad-token"))
                .thenThrow(new RuntimeException("malformed"));

        authFilter.doFilter(request, response, filterChain);

        verify(customAuthenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(BadCredentialsException.class)
        );
        verify(filterChain, never()).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_whenTokenValid_setsAuthenticationOnContext() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        UserDetails userDetails = new User("alice@example.com", "pw", Collections.emptyList());

        when(tokenService.getUsernameFromToken("good-token")).thenReturn("alice@example.com");
        when(customerUserDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(tokenService.isTokenValid("good-token", userDetails)).thenReturn(true);

        authFilter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_whenTokenInvalid_doesNotSetContextButContinuesChain() throws Exception {
        request.addHeader("Authorization", "Bearer stale-token");
        UserDetails userDetails = new User("alice@example.com", "pw", Collections.emptyList());

        when(tokenService.getUsernameFromToken("stale-token")).thenReturn("alice@example.com");
        when(customerUserDetailsService.loadUserByUsername("alice@example.com")).thenReturn(userDetails);
        when(tokenService.isTokenValid("stale-token", userDetails)).thenReturn(false);

        authFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_stripsBearerPrefixCorrectly() throws Exception {
        request.addHeader("Authorization", "Bearer my-token-value");
        UserDetails userDetails = new User("alice@example.com", "pw", Collections.emptyList());

        when(tokenService.getUsernameFromToken("my-token-value")).thenReturn("alice@example.com");
        when(customerUserDetailsService.loadUserByUsername(anyString())).thenReturn(userDetails);
        when(tokenService.isTokenValid("my-token-value", userDetails)).thenReturn(true);

        authFilter.doFilter(request, response, filterChain);

        verify(tokenService).getUsernameFromToken("my-token-value");
    }
}
