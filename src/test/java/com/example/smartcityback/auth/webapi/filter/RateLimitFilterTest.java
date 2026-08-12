package com.example.smartcityback.auth.webapi.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private FilterChain chain;

    @BeforeEach
    void setUp() {
        chain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("lets a request to a non-auth endpoint through the filter chain unthrottled, regardless of rate")
    void nonAuthPath_passesThrough() throws Exception {
        var filter = new RateLimitFilter(10, 10);
        var request = postRequest("/v1/buildings");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("lets an auth-endpoint request through when its rate-limit bucket still has capacity")
    void authPath_bucketNotExhausted_passesThrough() throws Exception {
        var filter = new RateLimitFilter(10, 10);
        var request = postRequest("/v1/auth/login");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("returns 429 on a second login request that exhausts a bucket sized to allow only one")
    void authPath_bucketExhausted_returns429() throws Exception {
        var filter = new RateLimitFilter(1, 1);

        filter.doFilter(postRequest("/v1/auth/login"), new MockHttpServletResponse(), chain);

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(postRequest("/v1/auth/login"), secondResponse, chain);

        assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    @DisplayName("rate-limits by the first IP in X-Forwarded-For rather than the proxy's own connection IP, "
            + "so it doesn't lump every client behind the same proxy into one shared bucket")
    void xForwardedFor_usesFirstIp_separateBucketFromDirect() throws Exception {
        // X-Forwarded-For lists the original client IP first, then each proxy hop that relayed
        // the request. Bucketing on the raw connection IP (the last proxy) would lump every
        // client behind that proxy into one shared limit; bucketing on the whole header string
        // would let a client dodge the limit just by varying the trailing hop values. Parsing
        // the first entry is what keys the bucket to the actual client.
        var filter = new RateLimitFilter(1, 1);
        var proxiedRequest = postRequest("/v1/auth/login");
        proxiedRequest.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");

        var response = new MockHttpServletResponse();
        filter.doFilter(proxiedRequest, response, chain);

        verify(chain).doFilter(proxiedRequest, response);
    }

    @Test
    @DisplayName("rate-limits the token-refresh endpoint the same way as login, not just /v1/auth/login")
    void authRefreshPath_isAlsoRateLimited() throws Exception {
        var filter = new RateLimitFilter(1, 1);

        filter.doFilter(postRequest("/v1/auth/refresh"), new MockHttpServletResponse(), chain);

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(postRequest("/v1/auth/refresh"), secondResponse, chain);

        assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private MockHttpServletRequest postRequest(String uri) {
        var request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(uri);
        return request;
    }
}
