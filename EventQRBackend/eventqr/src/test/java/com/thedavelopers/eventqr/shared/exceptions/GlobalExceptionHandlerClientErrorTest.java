package com.thedavelopers.eventqr.shared.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerClientErrorTest {

    @RestController
    static class Probe {
        @GetMapping("/probe/{id}")
        String byId(@PathVariable UUID id) { return id.toString(); }

        @GetMapping("/probe")
        String withParam(@RequestParam String q) { return q; }

        @PostMapping("/probe")
        String body(@RequestBody java.util.Map<String, String> body) { return "ok"; }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void malformedUuidInThePathIsABadRequestNotAServerError() throws Exception {
        mvc.perform(get("/probe/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredParameterIsABadRequest() throws Exception {
        mvc.perform(get("/probe")).andExpect(status().isBadRequest());
    }

    @Test
    void unreadableJsonIsABadRequest() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{not json")).andExpect(status().isBadRequest());
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mvc.perform(post("/probe/" + UUID.randomUUID())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType());
    }
}
