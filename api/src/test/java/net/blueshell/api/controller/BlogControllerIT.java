package net.blueshell.api.controller;

import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.BlogDTO;
import net.blueshell.api.factory.UnifiedFactory;
import net.blueshell.api.model.User;
import net.blueshell.api.testsupport.UserTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for BlogController endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BlogControllerIT extends UserTestSupport {

    private final Map<Role, User> users = new EnumMap<>(Role.class);

    private final UnifiedFactory uf;

    @Autowired
    BlogControllerIT(UnifiedFactory uf) {
        this.uf = uf;
    }

    @BeforeEach
    void setup() {
        users.put(Role.BOARD, createUserWithRole(Role.BOARD));
    }

    /** Create a blog as a board user. */
    private BlogDTO givenBlogCreatedByBoard() throws Exception {
        var payload = uf.full(BlogDTO.class);
        var res = mvc.perform(post("/blogs")
                        .with(bearer(users.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();
        return mapper.readValue(res.getResponse().getContentAsByteArray(), BlogDTO.class);
    }

    @Test
    void postsAreCreatedCorrectly() throws Exception {
        var payload = uf.full(BlogDTO.class);

        mvc.perform(post("/blogs")
                        .with(bearer(users.get(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.publishedAt")
                        .value(payload.getPublishedAt().truncatedTo(ChronoUnit.SECONDS).toString()))
                .andExpect(jsonPath("$.title").value(payload.getTitle()));
    }

    @Test
    void fetchingBlogsWorks() throws Exception {
        givenBlogCreatedByBoard();

        mvc.perform(get("/blogs").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void fetchingBlogsByIdWorks() throws Exception {
        BlogDTO created = givenBlogCreatedByBoard();

        mvc.perform(get("/blogs/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.title").value(created.getTitle()));
    }

    @Test
    void deletingBlogsRemovesThem() throws Exception {
        BlogDTO created = givenBlogCreatedByBoard();

        mvc.perform(delete("/blogs/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isNoContent());

        mvc.perform(get("/blogs").with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/blogs/{id}", created.getId()).with(bearer(users.get(Role.BOARD))))
                .andExpect(status().isNotFound());
    }
}
