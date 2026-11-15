package com.shopapp.media.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.media.PostgresContainerSupport;
import com.shopapp.media.TestImages;
import com.shopapp.media.model.Media;
import com.shopapp.media.repository.MediaRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
class MediaControllerIT {

    private static final Path ROOT = TestImages.tempDirectory();
    private static final Path MEDIA_DIR = ROOT.resolve("media");
    private static final Path SECRET = ROOT.resolve("secret.txt");
    private static final String SECRET_TEXT = "top-secret-content";
    private static final String PUBLIC_URL = "http://api.example.test/media";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaRepository repository;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("file.directory", MEDIA_DIR::toString);
        registry.add("shopapp.public-url.media", () -> PUBLIC_URL);
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static MockMultipartHttpServletRequestBuilder upload(String fileName, String contentType, byte[] bytes) {
        MockMultipartHttpServletRequestBuilder request = multipart("/medias");
        request.file(new MockMultipartFile("multipartFile", fileName, contentType, bytes));
        return request;
    }

    @BeforeEach
    void setUp() throws IOException {
        repository.deleteAll();
        Files.writeString(SECRET, SECRET_TEXT);
    }

    private long uploadPng(String fileName) throws Exception {
        String body = mockMvc.perform(upload(fileName, "image/png", TestImages.image("png"))
                .param("caption", "Red dot").with(admin()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void assertNothingStored(long filesBefore) {
        assertThat(repository.count()).isZero();
        assertThat(TestImages.fileCount(MEDIA_DIR)).isEqualTo(filesBefore);
    }

    /** Files under ROOT other than the media directory: only the secret may exist there. */
    private void assertNothingOutsideMediaDir() {
        assertThat(TestImages.fileCount(ROOT) - TestImages.fileCount(MEDIA_DIR)).isEqualTo(1);
    }

    @Test
    void adminUploadsPng_returnsMetadata_andStoresFileUnderServerName() throws Exception {
        long before = TestImages.fileCount(MEDIA_DIR);

        mockMvc.perform(upload("red dot.png", "image/png", TestImages.image("png"))
                .param("caption", "Red dot").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.caption").value("Red dot"))
            .andExpect(jsonPath("$.fileName").value("red_dot.png"))
            .andExpect(jsonPath("$.mediaType").value("image/png"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(TestImages.fileCount(MEDIA_DIR)).isEqualTo(before + 1);
        Media media = repository.findAll().getFirst();
        assertThat(Files.exists(MEDIA_DIR.resolve(media.getFilePath()))).isTrue();
        assertThat(media.getFilePath()).isNotEqualTo("red_dot.png").endsWith(".png");
        assertNothingOutsideMediaDir();
    }

    @Test
    void uploadTypeComesFromContent_notFromNameOrClientContentType() throws Exception {
        mockMvc.perform(upload("photo.jpg", "image/jpeg", TestImages.image("png")).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaType").value("image/png"));
        mockMvc.perform(upload("anim.png", "image/png", TestImages.image("gif")).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaType").value("image/gif"));
        mockMvc.perform(upload("pic.png", "application/octet-stream", TestImages.image("jpg")).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mediaType").value("image/jpeg"));
    }

    @Test
    void fileNameOverride_isUsedAndSanitized() throws Exception {
        mockMvc.perform(upload("a.png", "image/png", TestImages.image("png"))
                .param("fileNameOverride", "  my picture.png ").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName").value("my_picture.png"));
    }

    @Test
    void traversalInFileNames_isNeutralised() throws Exception {
        mockMvc.perform(upload("../../evil.png", "image/png", TestImages.image("png"))
                .param("fileNameOverride", "../../../etc/passwd").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName").value("passwd"));
        mockMvc.perform(upload("..\\..\\evil.png", "image/png", TestImages.image("png")).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName").value("evil.png"));
        mockMvc.perform(upload("..", "image/png", TestImages.image("png")).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName", not(containsString(".."))));

        assertNothingOutsideMediaDir();
        assertThat(Files.readString(SECRET)).isEqualTo(SECRET_TEXT);
    }

    @Test
    void customerUpload_isForbidden_andNothingStored() throws Exception {
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(upload("a.png", "image/png", TestImages.image("png")).with(customer()))
            .andExpect(status().isForbidden());
        assertNothingStored(before);
    }

    @Test
    void anonymousUpload_isRefused_andNothingStored() throws Exception {
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(upload("a.png", "image/png", TestImages.image("png")))
            .andExpect(status().is4xxClientError());
        assertNothingStored(before);
    }

    @Test
    void textNamedPng_isBadRequest_andNothingStored() throws Exception {
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(upload("notes.png", "image/png", "just some text".getBytes(StandardCharsets.UTF_8))
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("JPEG, PNG, GIF")));
        assertNothingStored(before);
    }

    @Test
    void otherImageFormat_isBadRequest() throws Exception {
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(upload("pic.png", "image/png", TestImages.image("bmp")).with(admin()))
            .andExpect(status().isBadRequest());
        assertNothingStored(before);
    }

    @Test
    void truncatedPng_isBadRequest() throws Exception {
        byte[] png = TestImages.image("png");
        byte[] truncated = java.util.Arrays.copyOf(png, png.length / 2);
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(upload("pic.png", "image/png", truncated).with(admin()))
            .andExpect(status().isBadRequest());
        assertNothingStored(before);
    }

    @Test
    void missingOrEmptyFile_isBadRequest() throws Exception {
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(multipart("/medias").param("caption", "x").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(upload("a.png", "image/png", new byte[0]).with(admin()))
            .andExpect(status().isBadRequest());
        assertNothingStored(before);
    }

    @Test
    void fileOverTenMegabytes_isBadRequest() throws Exception {
        byte[] png = TestImages.image("png");
        byte[] big = java.util.Arrays.copyOf(png, 10 * 1024 * 1024 + 1);
        long before = TestImages.fileCount(MEDIA_DIR);
        mockMvc.perform(upload("big.png", "image/png", big).with(admin()))
            .andExpect(status().isBadRequest());
        assertNothingStored(before);
    }

    @Test
    void metadata_isPublic_withBrowserFacingUrl() throws Exception {
        long id = uploadPng("red.png");

        mockMvc.perform(get("/medias/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.caption").value("Red dot"))
            .andExpect(jsonPath("$.fileName").value("red.png"))
            .andExpect(jsonPath("$.mediaType").value("image/png"))
            .andExpect(jsonPath("$.url").value(PUBLIC_URL + "/medias/" + id + "/file/red.png"));
    }

    @Test
    void unknownMedia_isNotFound() throws Exception {
        mockMvc.perform(get("/medias/{id}", 999_999))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/medias/{id}/file/{name}", 999_999, "red.png"))
            .andExpect(status().isNotFound());
    }

    @Test
    void file_isPublic_withStoredContentType_andCacheHeaders() throws Exception {
        long id = uploadPng("red.png");

        mockMvc.perform(get("/medias/{id}/file/{name}", id, "red.png"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(TestImages.image("png")))
            .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
            .andExpect(header().string("Cache-Control", containsString("public")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void wrongFileName_isNotFound() throws Exception {
        long id = uploadPng("red.png");

        mockMvc.perform(get("/medias/{id}/file/{name}", id, "other.png"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/medias/{id}/file/{name}", id, "RED.PNG"))
            .andExpect(status().isNotFound());
    }

    @Test
    void traversalInFileUrl_isRefused_andNeverReadsOutsideMediaDir() throws Exception {
        long id = uploadPng("red.png");

        for (String path : new String[] {
            "/medias/" + id + "/file/..%2F..%2Fsecret.txt",
            "/medias/" + id + "/file/..%2F..%2Fetc%2Fpasswd",
            "/medias/" + id + "/file/../x",
            "/medias/" + id + "/file/..%5C..%5Csecret.txt",
            "/medias/" + id + "/file/%2e%2e%2fsecret.txt"}) {
            mockMvc.perform(get(URI.create(path)))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString(SECRET_TEXT))));
        }
    }

    @Test
    void adminDelete_removesRowAndFile() throws Exception {
        long id = uploadPng("red.png");
        Path stored = MEDIA_DIR.resolve(repository.findById(id).orElseThrow().getFilePath());
        assertThat(Files.exists(stored)).isTrue();

        mockMvc.perform(delete("/medias/{id}", id).with(customer()))
            .andExpect(status().isForbidden());
        assertThat(Files.exists(stored)).isTrue();

        mockMvc.perform(delete("/medias/{id}", id).with(admin()))
            .andExpect(status().isNoContent());

        assertThat(Files.exists(stored)).isFalse();
        assertThat(repository.existsById(id)).isFalse();
        mockMvc.perform(get("/medias/{id}/file/{name}", id, "red.png"))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/medias/{id}", id).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void listByIds_isPublic_andBounded() throws Exception {
        long first = uploadPng("a.png");
        long second = uploadPng("b.png");

        mockMvc.perform(get("/medias").param("ids", first + "," + second + ",999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].fileName", containsInAnyOrder("a.png", "b.png")))
            .andExpect(jsonPath("$[*].url", containsInAnyOrder(
                PUBLIC_URL + "/medias/" + first + "/file/a.png",
                PUBLIC_URL + "/medias/" + second + "/file/b.png")));

        mockMvc.perform(get("/medias").param("ids", "999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
        mockMvc.perform(get("/medias").param("ids", tooMany))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/medias"))
            .andExpect(status().isBadRequest());
    }
}
