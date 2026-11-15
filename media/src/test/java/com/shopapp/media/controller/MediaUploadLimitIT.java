package com.shopapp.media.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.media.PostgresContainerSupport;
import com.shopapp.media.TestImages;
import com.shopapp.media.repository.MediaRepository;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Upload size limit through a real Tomcat (MockMvc does not parse multipart bodies, so it never sees the limit).
 * A test {@link JwtDecoder} accepts one fixed token carrying the Keycloak realm role ADMIN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresContainerSupport.class, MediaUploadLimitIT.AdminTokenDecoder.class})
class MediaUploadLimitIT {

    private static final String ADMIN_TOKEN = "admin-token";
    private static final String BOUNDARY = "----media-it-boundary";
    private static final Path MEDIA_DIR = TestImages.tempDirectory().resolve("media");

    @LocalServerPort
    private int port;

    @Autowired
    private MediaRepository repository;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("file.directory", MEDIA_DIR::toString);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminTokenDecoder {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!ADMIN_TOKEN.equals(token)) {
                    throw new BadJwtException("unknown token");
                }
                return new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                    Map.of("sub", "admin", "realm_access", Map.of("roles", List.of("ADMIN"))));
            };
        }
    }

    private HttpResponse<String> upload(byte[] file) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"multipartFile\"; "
            + "filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/media/medias"))
            .header("Authorization", "Bearer " + ADMIN_TOKEN)
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build();
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void smallPng_isAccepted_withRealToken() throws Exception {
        HttpResponse<String> response = upload(TestImages.image("png"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"mediaType\":\"image/png\"");
    }

    /** A file of exactly 10 MB plus the multipart overhead still fits the request limit (decisions.md 3b-23). */
    @Test
    void fileOfExactlyTenMegabytes_isAccepted() throws Exception {
        byte[] png = TestImages.image("png");
        byte[] tenMegabytes = java.util.Arrays.copyOf(png, 10 * 1024 * 1024);

        HttpResponse<String> response = upload(tenMegabytes);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void fileOverTenMegabytes_isRejectedByTheMultipartLimit_andNothingStored() throws Exception {
        long rowsBefore = repository.count();
        long filesBefore = TestImages.fileCount(MEDIA_DIR);
        byte[] png = TestImages.image("png");
        byte[] big = java.util.Arrays.copyOf(png, 10 * 1024 * 1024 + 1024);

        HttpResponse<String> response = upload(big);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"statusCode\"");
        assertThat(repository.count()).isEqualTo(rowsBefore);
        assertThat(TestImages.fileCount(MEDIA_DIR)).isEqualTo(filesBefore);
    }
}
