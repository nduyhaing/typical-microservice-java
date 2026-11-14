package com.shopapp.product.service;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.product.viewmodel.NoFileMediaVm;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Looks up image URLs in the media service. Until the media service exists (slice 3) its URL is not
 * configured, and every lookup returns an empty URL so the storefront shows its placeholder image.
 */
@Slf4j
@Service
public class MediaService {

    private final ServiceUrlConfig serviceUrlConfig;
    private final RestClient restClient = RestClient.create();

    public MediaService(ServiceUrlConfig serviceUrlConfig) {
        this.serviceUrlConfig = serviceUrlConfig;
    }

    public NoFileMediaVm getMedia(Long id) {
        String mediaUrl = serviceUrlConfig.media();
        if (id == null || mediaUrl == null || mediaUrl.isBlank()) {
            return NoFileMediaVm.empty(id);
        }
        URI url = UriComponentsBuilder.fromUriString(mediaUrl).path("/medias/{id}").buildAndExpand(id).toUri();
        try {
            NoFileMediaVm media = restClient.get().uri(url).retrieve().body(NoFileMediaVm.class);
            return media != null ? media : NoFileMediaVm.empty(id);
        } catch (RestClientException ex) {
            log.warn("Media {} unavailable: {}", id, ex.getMessage());
            return NoFileMediaVm.empty(id);
        }
    }
}
