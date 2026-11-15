package com.shopapp.backofficebff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.annotation.Import;
import reactor.netty.http.client.HttpClient;

/**
 * A recreated upstream container (new IP) is reached without restarting the BFF (decisions.md 4-80): the gateway's
 * client caches DNS answers 10 s at most (Docker's DNS says 600 s) and does not keep idle or old pooled connections.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(RedisContainerSupport.class)
class GatewayConnectionFreshnessTest {

    @Autowired
    private HttpClient gatewayHttpClient;

    @Autowired
    private HttpClientProperties properties;

    @Test
    void dnsAnswersAreCachedTenSecondsAtMost() {
        var resolver = gatewayHttpClient.configuration().getNameResolverProvider();
        assertThat(resolver).isNotNull();
        assertThat(resolver.cacheMaxTimeToLive()).isEqualTo(Duration.ofSeconds(10));
        assertThat(resolver.cacheNegativeTimeToLive()).isEqualTo(Duration.ZERO);
    }

    @Test
    void pooledConnectionsAreDroppedWhenIdleOrOld() {
        assertThat(properties.getPool().getMaxIdleTime()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getPool().getMaxLifeTime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.getPool().getEvictionInterval()).isEqualTo(Duration.ofSeconds(30));
    }
}
