package net.blueshell.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.BlogDTO;
import net.blueshell.api.model.User;
import net.blueshell.api.service.BlogService;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for BlogController, split into creation, retrieval, and deletion scenarios.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "brevo-mock"})
class BlogControllerIT extends UserTestSupport {

    private final Map<Role, User> userMap = new EnumMap<>(Role.class);
    Map<String, Object> examplePayload = Map.of(
            "title", "New Blog",
            "publishedAt", "2025-07-01T12:00:00.000+00:00",
            "html", "<div><span>cool story bro</span></div>"
    );
    @Autowired
    BlogService blogService;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        userMap.put(Role.MEMBER, createUserWithRole(Role.MEMBER));
        userMap.put(Role.BOARD, createUserWithRole(Role.BOARD));
    }

    @Test
    void postsAreCreatedCorrectly() throws Exception {
        mvc.perform(post("/blogs")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt").value(examplePayload.get("publishedAt")))
                .andExpect(jsonPath("$.title").value(examplePayload.get("title")))
                .andReturn();
    }

    @Test
    void fetchingBlogsWorks() throws Exception {
        mvc.perform(post("/blogs")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt").value(examplePayload.get("publishedAt")))
                .andExpect(jsonPath("$.title").value(examplePayload.get("title")))
                .andReturn();

        mvc.perform(get("/blogs").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void fetchingBlogsByIdWorks() throws Exception {
        MvcResult result = mvc.perform(post("/blogs")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt").value(examplePayload.get("publishedAt")))
                .andExpect(jsonPath("$.title").value(examplePayload.get("title")))
                .andReturn();

        BlogDTO dto = mapper.readValue(result.getResponse().getContentAsByteArray(), BlogDTO.class);

        mvc.perform(get("/blogs/{id}", dto.getId()).with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().toString()))
                .andExpect(jsonPath("$.publishedAt").value(examplePayload.get("publishedAt")))
                .andExpect(jsonPath("$.title").value(examplePayload.get("title")));
    }

    @Test
    void deletingBlogsRemovesThem() throws Exception {
        MvcResult result = mvc.perform(post("/blogs")
                        .with(bearer(userMap.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(examplePayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt").value(examplePayload.get("publishedAt")))
                .andExpect(jsonPath("$.title").value(examplePayload.get("title")))
                .andReturn();

        BlogDTO dto = mapper.readValue(result.getResponse().getContentAsByteArray(), BlogDTO.class);

        log.info("Blog count?: {}", blogService.findAll());
        mvc.perform(delete("/blogs/{id}", dto.getId()).with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/blogs").with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/blogs/{id}", dto.getId()).with(bearer(userMap.get(Role.BOARD))))
                .andExpect(status().isNotFound());
    }
}