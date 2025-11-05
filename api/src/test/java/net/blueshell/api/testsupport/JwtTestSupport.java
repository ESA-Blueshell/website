package net.blueshell.api.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.blueshell.api.dto.request.JwtRequest;
import net.blueshell.api.dto.response.AuthenticationDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for tests that require an authenticated Bearer token.
 * The admin token is lazily fetched and cached per-JVM to speed up suites.
 */
@Component
public abstract class JwtTestSupport {

    private static String cachedAdminToken;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    /**
     * Obtain (and cache) a JWT for the seeded admin/admin account.
     */
    protected String adminToken() throws Exception {
        if (cachedAdminToken != null) {
            return cachedAdminToken;
        }

        JwtRequest requestBody = new JwtRequest("admin", "admin");

        MvcResult result = mvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        AuthenticationDTO response =
                mapper.readValue(result.getResponse().getContentAsByteArray(), AuthenticationDTO.class);

        return cachedAdminToken = response.getToken();
    }

    /**
     * Convenience wrapper to apply Authorization: Bearer <token>.
     */
    protected RequestPostProcessor bearer() throws Exception {
        String token = adminToken();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
