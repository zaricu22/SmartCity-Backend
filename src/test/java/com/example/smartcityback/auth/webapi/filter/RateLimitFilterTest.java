package com.example.smartcityback.auth.webapi.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
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
    void nonAuthPath_passesThrough() throws Exception {
        var filter = new RateLimitFilter(10, 10);
        var request = postRequest("/v1/buildings");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void authPath_bucketNotExhausted_passesThrough() throws Exception {
        var filter = new RateLimitFilter(10, 10);
        var request = postRequest("/v1/auth/login");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void authPath_bucketExhausted_returns429() throws Exception {
        var filter = new RateLimitFilter(1, 1);

        filter.doFilter(postRequest("/v1/auth/login"), new MockHttpServletResponse(), chain);

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(postRequest("/v1/auth/login"), secondResponse, chain);

        assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void xForwardedFor_usesFirstIp_separateBucketFromDirect() throws Exception {
        var filter = new RateLimitFilter(1, 1);
        var proxiedRequest = postRequest("/v1/auth/login");
        proxiedRequest.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");

        var response = new MockHttpServletResponse();
        filter.doFilter(proxiedRequest, response, chain);

        verify(chain).doFilter(proxiedRequest, response);
    }

    @Test
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
