package com.shopapp.product.service;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.commonlibrary.observability.ObservedRestClient;
import com.shopapp.product.viewmodel.NoFileMediaVm;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Looks up image URLs in the media service. A missing id, an unconfigured media URL, an unknown media or any
 * media-service failure gives an empty URL, so the storefront shows its placeholder image (decisions.md 2a-11). Lists
 * resolve all their images with one {@code GET /medias?ids=} call (3b-10).
 *
 * <p>Images are optional, so the media service gets a much shorter budget than the 5 s callers (cart, order) allow
 * for product: 500 ms connect, 1.5 s read. After {@code failureThreshold} consecutive failures (timeouts, refused
 * connections, 5xx) a breaker skips every call for {@code openDuration}, so a dead media service costs nothing
 * (decisions.md 3b-18). Callers resolve URLs outside their database transaction (3b-19).
 */
@Slf4j
@Service
public class MediaService {

    /** The media service accepts at most this many ids per list request. */
    static final int MAX_IDS_PER_REQUEST = 100;

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMillis(500);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofMillis(1500);
    static final int DEFAULT_FAILURE_THRESHOLD = 5;
    static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(30);

    private static final ParameterizedTypeReference<List<NoFileMediaVm>> MEDIA_LIST =
        new ParameterizedTypeReference<>() { };

    private final ServiceUrlConfig serviceUrlConfig;
    private final RestClient restClient;
    private final int failureThreshold;
    private final long openNanos;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilNanos = new AtomicLong();
    private volatile boolean open;

    @Autowired
    public MediaService(ServiceUrlConfig serviceUrlConfig,
                        @Value("${shopapp.media.connect-timeout:500ms}") Duration connectTimeout,
                        @Value("${shopapp.media.read-timeout:1500ms}") Duration readTimeout,
                        @Value("${shopapp.media.breaker.failure-threshold:5}") int failureThreshold,
                        @Value("${shopapp.media.breaker.open-duration:30s}") Duration openDuration) {
        this.serviceUrlConfig = serviceUrlConfig;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = ObservedRestClient.builder().requestFactory(requestFactory).build();
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openNanos = openDuration.toNanos();
    }

    MediaService(ServiceUrlConfig serviceUrlConfig) {
        this(serviceUrlConfig, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    MediaService(ServiceUrlConfig serviceUrlConfig, Duration connectTimeout, Duration readTimeout) {
        this(serviceUrlConfig, connectTimeout, readTimeout, DEFAULT_FAILURE_THRESHOLD, DEFAULT_OPEN_DURATION);
    }

    public NoFileMediaVm getMedia(Long id) {
        String mediaUrl = serviceUrlConfig.media();
        if (id == null || mediaUrl == null || mediaUrl.isBlank() || breakerOpen()) {
            return NoFileMediaVm.empty(id);
        }
        URI url = UriComponentsBuilder.fromUriString(mediaUrl).path("/medias/{id}").buildAndExpand(id).toUri();
        try {
            NoFileMediaVm media = restClient.get().uri(url).retrieve().body(NoFileMediaVm.class);
            recordSuccess();
            return media != null && media.url() != null ? media : NoFileMediaVm.empty(id);
        } catch (HttpClientErrorException ex) {
            recordSuccess();
            log.debug("Media {} not found: {}", id, ex.getMessage());
            return NoFileMediaVm.empty(id);
        } catch (RestClientException ex) {
            recordFailure();
            log.warn("Media {} unavailable: {}", id, ex.getMessage());
            return NoFileMediaVm.empty(id);
        }
    }

    /**
     * URLs of the given media ids (nulls and duplicates ignored), one request per 100 ids. Unknown ids are absent
     * from the map; a failed request leaves its ids absent. Read with {@link #urlOf(Map, Long)}.
     */
    public Map<Long, String> getMediaUrls(Collection<Long> ids) {
        String mediaUrl = serviceUrlConfig.media();
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> urls = new HashMap<>();
        if (distinct.isEmpty() || mediaUrl == null || mediaUrl.isBlank()) {
            return urls;
        }
        for (int from = 0; from < distinct.size() && !breakerOpen(); from += MAX_IDS_PER_REQUEST) {
            List<Long> chunk = distinct.subList(from, Math.min(from + MAX_IDS_PER_REQUEST, distinct.size()));
            URI url = UriComponentsBuilder.fromUriString(mediaUrl).path("/medias")
                .queryParam("ids", String.join(",", chunk.stream().map(String::valueOf).toList()))
                .build().toUri();
            try {
                List<NoFileMediaVm> medias = restClient.get().uri(url).retrieve().body(MEDIA_LIST);
                recordSuccess();
                if (medias != null) {
                    medias.stream()
                        .filter(media -> media.id() != null && media.url() != null)
                        .forEach(media -> urls.put(media.id(), media.url()));
                }
            } catch (HttpClientErrorException ex) {
                recordSuccess();
                log.warn("Media {} refused: {}", chunk, ex.getMessage());
            } catch (RestClientException ex) {
                recordFailure();
                log.warn("Media {} unavailable: {}", chunk, ex.getMessage());
            }
        }
        return urls;
    }

    /** True while the breaker skips calls; once the open window has passed the next call is let through. */
    private boolean breakerOpen() {
        return open && System.nanoTime() - openUntilNanos.get() < 0;
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        open = false;
    }

    /** Opens the breaker at the threshold; a failed trial call after the window re-opens it at once. */
    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntilNanos.set(System.nanoTime() + openNanos);
            if (!open) {
                log.warn("Media service failed {} times in a row; skipping media calls for {} ms", failureThreshold,
                    Duration.ofNanos(openNanos).toMillis());
            }
            open = true;
        }
    }

    /** The URL of {@code id} in a {@link #getMediaUrls(Collection)} result; empty when absent or null. */
    public static String urlOf(Map<Long, String> urls, Long id) {
        return id == null ? "" : urls.getOrDefault(id, "");
    }
}
