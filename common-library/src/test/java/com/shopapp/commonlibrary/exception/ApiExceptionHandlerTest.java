package com.shopapp.commonlibrary.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    record NameRequest(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("RESOURCE_NOT_FOUND", "Product", 7);
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("bad input");
        }

        @GetMapping("/duplicated")
        void duplicated() {
            throw new DuplicatedException("already there");
        }

        @GetMapping("/resource-existed")
        void resourceExisted() {
            throw new ResourceExistedException("exists");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("ACCESS_DENIED");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("not yours");
        }

        @GetMapping("/sign-in-required")
        void signInRequired() {
            throw new SignInRequiredException("SIGN_IN_REQUIRED");
        }

        @GetMapping("/internal")
        void internal() {
            throw new InternalServerErrorException("payment provider down");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("java.lang.secret internals");
        }

        @GetMapping("/param")
        void param(@RequestParam String id) {
            // only reached when the parameter is present
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody NameRequest request) {
            // only reached when the body is valid
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void notFound_returns404WithMessage() throws Exception {
        mockMvc.perform(get("/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404 NOT_FOUND"))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.detail").value("Resource Product with id 7 not found"))
            .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void badRequest_returns400() throws Exception {
        mockMvc.perform(get("/bad-request"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400 BAD_REQUEST"))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.detail").value("bad input"));
    }

    @Test
    void duplicated_returns400() throws Exception {
        mockMvc.perform(get("/duplicated"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("already there"));
    }

    @Test
    void resourceExisted_returns409() throws Exception {
        mockMvc.perform(get("/resource-existed"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.statusCode").value("409 CONFLICT"))
            .andExpect(jsonPath("$.detail").value("exists"));
    }

    @Test
    void accessDenied_returns403() throws Exception {
        mockMvc.perform(get("/access-denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403 FORBIDDEN"))
            .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void forbidden_returns403() throws Exception {
        mockMvc.perform(get("/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("not yours"));
    }

    @Test
    void signInRequired_returns403() throws Exception {
        mockMvc.perform(get("/sign-in-required"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void internalServerError_returns500WithMessage() throws Exception {
        mockMvc.perform(get("/internal"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.statusCode").value("500 INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.detail").value("payment provider down"));
    }

    @Test
    void unexpected_returns500WithoutInternals() throws Exception {
        String body = mockMvc.perform(get("/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Internal Server Error"))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("java.");
    }

    @Test
    void missingParam_returns400() throws Exception {
        mockMvc.perform(get("/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400 BAD_REQUEST"));
    }

    @Test
    void validation_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request information is not valid"))
            .andExpect(jsonPath("$.fieldErrors[0]").value("name must not be blank"));
    }
}
