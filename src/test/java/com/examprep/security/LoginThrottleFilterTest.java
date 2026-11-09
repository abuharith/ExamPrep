package com.examprep.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginThrottleFilterTest {

    private static MockHttpServletRequest loginFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr(address);
        return request;
    }

    @Test
    void blocksAfterTheLimitAndRecoversWhenTheWindowPasses() throws Exception {
        long[] now = {0L};
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now[0]); }
        };
        LoginThrottleFilter filter = new LoginThrottleFilter(3, 1000, clock);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(loginFrom("10.0.0.1"), ok, new MockFilterChain());
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginFrom("10.0.0.1"), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("1");

        MockHttpServletResponse otherClient = new MockHttpServletResponse();
        filter.doFilter(loginFrom("10.0.0.2"), otherClient, new MockFilterChain());
        assertThat(otherClient.getStatus()).isEqualTo(200);

        now[0] = 2000L;
        MockHttpServletResponse afterWindow = new MockHttpServletResponse();
        filter.doFilter(loginFrom("10.0.0.1"), afterWindow, new MockFilterChain());
        assertThat(afterWindow.getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresOtherRequests() throws Exception {
        LoginThrottleFilter filter = new LoginThrottleFilter(1, 1000, Clock.systemUTC());
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/login");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(get, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
