package com.shopapp.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.commonlibrary.config.ServiceUrlConfig;
import com.shopapp.product.support.MediaStub;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MediaServiceTest {

    private final MediaStub stub = new MediaStub();

    @AfterEach
    void stop() {
        stub.close();
    }

    private MediaService service() {
        return new MediaService(new ServiceUrlConfig(stub.baseUrl(), null));
    }

    @Test
    void nullMediaId_returnsEmptyUrlWithoutHttp() {
        assertThat(service().getMedia(null).url()).isEmpty();
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void blankMediaServiceUrl_returnsEmptyUrl() {
        MediaService mediaService = new MediaService(new ServiceUrlConfig("", null));
        assertThat(mediaService.getMedia(42L).url()).isEmpty();
        assertThat(mediaService.getMediaUrls(List.of(42L))).isEmpty();
    }

    @Test
    void unreachableMediaService_returnsEmptyUrl() {
        MediaService mediaService = new MediaService(new ServiceUrlConfig("http://127.0.0.1:1", null));
        assertThat(mediaService.getMedia(42L).url()).isEmpty();
        assertThat(mediaService.getMediaUrls(List.of(42L))).isEmpty();
    }

    @Test
    void getMedia_returnsUrlFromMediaService() {
        assertThat(service().getMedia(7L).url()).isEqualTo(MediaStub.urlOf(7));
        assertThat(stub.requests()).containsExactly("/media/medias/7");
    }

    @Test
    void getMedia_unknownId_returnsEmptyUrl() {
        stub.missing(7L);
        assertThat(service().getMedia(7L).url()).isEmpty();
    }

    @Test
    void slowMediaService_givesUpWellBeforeCallersFiveSecondTimeout() {
        stub.delay(Duration.ofSeconds(4));
        long start = System.nanoTime();
        String url = service().getMedia(7L).url();
        Map<Long, String> urls = service().getMediaUrls(List.of(7L));
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        assertThat(url).isEmpty();
        assertThat(urls).isEmpty();
        assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(2800)).isLessThan(Duration.ofMillis(4000));
    }

    @Test
    void breaker_opensAfterConsecutiveFailures_andSkipsFurtherCalls() {
        stub.delay(Duration.ofSeconds(2));
        MediaService mediaService = new MediaService(new ServiceUrlConfig(stub.baseUrl(), null),
            Duration.ofMillis(500), Duration.ofMillis(200), 3, Duration.ofSeconds(30));
        for (int i = 0; i < 3; i++) {
            assertThat(mediaService.getMediaUrls(List.of(1L))).isEmpty();
        }
        long start = System.nanoTime();
        assertThat(mediaService.getMediaUrls(List.of(1L))).isEmpty();
        assertThat(mediaService.getMedia(1L).url()).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));
        assertThat(stub.requests()).hasSize(3);
    }

    @Test
    void breaker_closesAfterOpenWindow() throws InterruptedException {
        stub.delay(Duration.ofSeconds(1));
        MediaService mediaService = new MediaService(new ServiceUrlConfig(stub.baseUrl(), null),
            Duration.ofMillis(500), Duration.ofMillis(200), 2, Duration.ofMillis(500));
        mediaService.getMediaUrls(List.of(1L));
        mediaService.getMediaUrls(List.of(1L));
        stub.delay(Duration.ZERO);
        assertThat(mediaService.getMediaUrls(List.of(1L))).isEmpty();
        assertThat(stub.requests()).hasSize(2);

        Thread.sleep(700);
        assertThat(mediaService.getMediaUrls(List.of(1L))).containsEntry(1L, MediaStub.urlOf(1));
        assertThat(stub.requests()).hasSize(3);
    }

    @Test
    void breaker_successResetsFailureCount() {
        MediaService mediaService = new MediaService(new ServiceUrlConfig(stub.baseUrl(), null),
            Duration.ofMillis(500), Duration.ofMillis(200), 2, Duration.ofSeconds(30));
        stub.delay(Duration.ofSeconds(1));
        mediaService.getMediaUrls(List.of(1L));
        stub.delay(Duration.ZERO);
        mediaService.getMediaUrls(List.of(1L));
        stub.delay(Duration.ofSeconds(1));
        mediaService.getMediaUrls(List.of(1L));
        stub.delay(Duration.ZERO);
        assertThat(mediaService.getMediaUrls(List.of(1L))).containsEntry(1L, MediaStub.urlOf(1));
        assertThat(stub.requests()).hasSize(4);
    }

    @Test
    void unknownMedia_doesNotOpenBreaker() {
        stub.missing(7L);
        MediaService mediaService = new MediaService(new ServiceUrlConfig(stub.baseUrl(), null),
            Duration.ofMillis(500), Duration.ofMillis(500), 2, Duration.ofSeconds(30));
        for (int i = 0; i < 4; i++) {
            assertThat(mediaService.getMedia(7L).url()).isEmpty();
        }
        assertThat(stub.requests()).hasSize(4);
    }

    @Test
    void slowMediaService_batchGivesUpWithShortTimeout() {
        stub.delay(Duration.ofSeconds(3));
        MediaService mediaService = new MediaService(new ServiceUrlConfig(stub.baseUrl(), null),
            Duration.ofMillis(500), Duration.ofMillis(300));
        long start = System.nanoTime();
        Map<Long, String> urls = mediaService.getMediaUrls(List.of(1L, 2L));
        assertThat(urls).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2000));
    }

    @Test
    void getMediaUrls_oneRequestForDistinctNonNullIds() {
        stub.missing(3L);
        Map<Long, String> urls = service().getMediaUrls(Arrays.asList(1L, null, 2L, 1L, 3L));
        assertThat(urls).containsOnly(Map.entry(1L, MediaStub.urlOf(1)), Map.entry(2L, MediaStub.urlOf(2)));
        assertThat(stub.requests()).containsExactly("/media/medias?ids=1,2,3");
    }

    @Test
    void getMediaUrls_noIds_noRequest() {
        assertThat(service().getMediaUrls(Arrays.<Long>asList(null, null))).isEmpty();
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    void getMediaUrls_moreThan100Ids_splitsIntoChunksOf100() {
        List<Long> ids = LongStream.rangeClosed(1, 150).boxed().toList();
        Map<Long, String> urls = service().getMediaUrls(ids);
        assertThat(urls).hasSize(150);
        assertThat(stub.requests()).hasSize(2);
        assertThat(stub.listedIds()).containsExactlyElementsOf(ids);
    }
}
