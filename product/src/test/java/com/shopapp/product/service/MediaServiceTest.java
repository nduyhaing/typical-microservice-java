package com.shopapp.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import org.junit.jupiter.api.Test;

class MediaServiceTest {

    @Test
    void nullMediaId_returnsEmptyUrlWithoutHttp() {
        MediaService mediaService = new MediaService(new ServiceUrlConfig("http://media.invalid", null));
        assertThat(mediaService.getMedia(null).url()).isEmpty();
    }

    @Test
    void blankMediaServiceUrl_returnsEmptyUrl() {
        MediaService mediaService = new MediaService(new ServiceUrlConfig("", null));
        assertThat(mediaService.getMedia(42L).url()).isEmpty();
    }

    @Test
    void unreachableMediaService_returnsEmptyUrl() {
        MediaService mediaService = new MediaService(new ServiceUrlConfig("http://127.0.0.1:1", null));
        assertThat(mediaService.getMedia(42L).url()).isEmpty();
    }
}
