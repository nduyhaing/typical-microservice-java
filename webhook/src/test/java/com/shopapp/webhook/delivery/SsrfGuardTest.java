package com.shopapp.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopapp.webhook.config.WebhookProperties;
import com.shopapp.webhook.support.FakeHostResolver;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SsrfGuardTest {

    private final FakeHostResolver resolver = new FakeHostResolver()
        .map("hooks.example.com", "93.184.216.34")
        .map("v6.example.com", "2606:4700:4700::1111")
        .map("mixed.example.com", "93.184.216.34", "10.0.0.7")
        .map("internal.example.com", "10.1.2.3")
        .map("metadata.example.com", "169.254.169.254")
        .map("receiver", "172.18.0.9");

    private final SsrfGuard guard = new SsrfGuard(resolver, properties(List.of(" Receiver ")));

    private static WebhookProperties properties(List<String> allowedHosts) {
        return new WebhookProperties(allowedHosts, new WebhookProperties.Delivery(
            2000, 5000, 10000, 3, 100, 2, 4, 100, 60000, 65536));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://hooks.example.com/shop", "http://hooks.example.com:8080/a?b=c", "HTTPS://HOOKS.example.com",
        "https://v6.example.com/x", "https://93.184.216.34/x", "http://[2606:4700:4700::1111]/x",
        "http://receiver:8080/hook", "http://RECEIVER/hook"})
    void publicOrAllowListedUrls_areAccepted(String url) {
        assertThatCode(() -> guard.checkForRegistration(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://hooks.example.com/x", "file:///etc/passwd", "gopher://hooks.example.com/", "javascript:alert(1)",
        "//hooks.example.com/x", "hooks.example.com/x", "not a url", "http://", "http:///path",
        "http://under_score.example.com/", "http://hooks.example.com:0/", "http://hooks.example.com:99999/",
        "http://127.1/"})
    void malformedOrNonHttpUrls_areRefused(String url) {
        assertThatThrownBy(() -> guard.checkForRegistration(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://user:pass@hooks.example.com/", "https://token@hooks.example.com/"})
    void credentialsInTheUrl_areRefused(String url) {
        assertThatThrownBy(() -> guard.checkForRegistration(url))
            .isInstanceOf(UnsafeUrlException.class)
            .hasMessageContaining("credentials");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/", "http://127.0.0.1/", "http://2130706433/", "http://0.0.0.0/",
        "http://[::1]/", "http://[::ffff:127.0.0.1]/", "http://10.0.0.1/", "http://172.16.5.4/",
        "http://192.168.1.1/", "http://169.254.169.254/latest/meta-data/", "http://100.64.0.1/",
        "http://224.0.0.1/", "http://[fd00::1]/", "http://[fe80::1]/", "http://internal.example.com/",
        "http://metadata.example.com/", "http://mixed.example.com/"})
    void hostsResolvingToNonPublicAddresses_areRefused(String url) {
        assertThatThrownBy(() -> guard.checkForRegistration(url))
            .isInstanceOf(UnsafeUrlException.class)
            .hasMessageContaining("non-public");
    }

    @Test
    void unresolvableHost_isRefused() {
        assertThatThrownBy(() -> guard.checkForRegistration("https://nowhere.invalid/x"))
            .isInstanceOf(UnsafeUrlException.class)
            .hasMessageContaining("cannot be resolved");
    }

    @Test
    void tooLongUrl_isRefused() {
        String url = "https://hooks.example.com/" + "a".repeat(2048);
        assertThatThrownBy(() -> guard.checkForRegistration(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void resolveForConnect_returnsTheCheckedAddresses_orRefuses() throws Exception {
        assertThat(guard.resolveForConnect("hooks.example.com"))
            .containsExactly(InetAddress.getByName("93.184.216.34"));
        assertThat(guard.resolveForConnect("receiver")).containsExactly(InetAddress.getByName("172.18.0.9"));

        // DNS rebinding: registered while public, resolves to a private address at send time
        resolver.map("hooks.example.com", "10.9.9.9");
        assertThatThrownBy(() -> guard.resolveForConnect("hooks.example.com"))
            .isInstanceOf(BlockedAddressException.class);
        assertThatThrownBy(() -> guard.resolveForConnect("[::1]")).isInstanceOf(BlockedAddressException.class);
    }

    @Test
    void withoutAllowList_aComposeHostIsRefused() {
        SsrfGuard strict = new SsrfGuard(resolver, properties(List.of()));
        assertThatThrownBy(() -> strict.checkForRegistration("http://receiver:8080/hook"))
            .isInstanceOf(UnsafeUrlException.class);
    }
}
