package com.shopapp.product.service;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.product.viewmodel.NoFileMediaVm;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Looks up image URLs in the media service, with connect/read timeouts. A missing id, an unconfigured media URL, an
 * unknown media or any media-service failure gives an empty URL, so the storefront shows its placeholder image
 * (decisions.md 2a-11). Lists resolve all their images with one {@code GET /medias?ids=} call (3b-10).
 */
@Slf4j
@Service
public class MediaService {

    /** The media service accepts at most this many ids per list request. */
    static final int MAX_IDS_PER_REQUEST = 100;

    private static final ParameterizedTypeReference<List<NoFileMediaVm>> MEDIA_LIST =
        new ParameterizedTypeReference<>() { };

    private final ServiceUrlConfig serviceUrlConfig;
    private final RestClient restClient;

    @Autowired
    public MediaService(ServiceUrlConfig serviceUrlConfig) {
        this(serviceUrlConfig, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    MediaService(ServiceUrlConfig serviceUrlConfig, Duration connectTimeout, Duration readTimeout) {
        this.serviceUrlConfig = serviceUrlConfig;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public NoFileMediaVm getMedia(Long id) {
        String mediaUrl = serviceUrlConfig.media();
        if (id == null || mediaUrl == null || mediaUrl.isBlank()) {
            return NoFileMediaVm.empty(id);
        }
        URI url = UriComponentsBuilder.fromUriString(mediaUrl).path("/medias/{id}").buildAndExpand(id).toUri();
        try {
            NoFileMediaVm media = restClient.get().uri(url).retrieve().body(NoFileMediaVm.class);
            return media != null && media.url() != null ? media : NoFileMediaVm.empty(id);
        } catch (RestClientException ex) {
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
        for (int from = 0; from < distinct.size(); from += MAX_IDS_PER_REQUEST) {
            List<Long> chunk = distinct.subList(from, Math.min(from + MAX_IDS_PER_REQUEST, distinct.size()));
            URI url = UriComponentsBuilder.fromUriString(mediaUrl).path("/medias")
                .queryParam("ids", String.join(",", chunk.stream().map(String::valueOf).toList()))
                .build().toUri();
            try {
                List<NoFileMediaVm> medias = restClient.get().uri(url).retrieve().body(MEDIA_LIST);
                if (medias != null) {
                    medias.stream()
                        .filter(media -> media.id() != null && media.url() != null)
                        .forEach(media -> urls.put(media.id(), media.url()));
                }
            } catch (RestClientException ex) {
                log.warn("Media {} unavailable: {}", chunk, ex.getMessage());
            }
        }
        return urls;
    }

    /** The URL of {@code id} in a {@link #getMediaUrls(Collection)} result; empty when absent or null. */
    public static String urlOf(Map<Long, String> urls, Long id) {
        return id == null ? "" : urls.getOrDefault(id, "");
    }
}
