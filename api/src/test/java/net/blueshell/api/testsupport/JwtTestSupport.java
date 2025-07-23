package net.blueshell.api.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.blueshell.api.controller.request.JwtRequest;
import net.blueshell.api.controller.response.JwtResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Super-class for tests that need an authenticated Bearer token.
 * Call {@link #adminToken()} once — the token is cached for the whole JVM
 * (fastest when you run many tests in the same Maven fork).
 * Typical usage :
 * <pre>{@code
 * class BlogControllerIT extends JwtTestSupport {
 *     @Test void securedCall() throws Exception {
 *         mockMvc.perform(get("/blogs").with(bearer()))
 *                .andExpect(status().isOk());
 *     }
 * }
 * }</pre>
 */
public abstract class JwtTestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;

    /* lazily initialised, then reused */
    private static String cachedAdminToken;

    /** Obtain (and cache) a JWT for the seeded <i>admin/admin</i> account. */
    protected String adminToken() throws Exception {
        if (cachedAdminToken != null) {
            return cachedAdminToken;
        }

        JwtRequest requestBody = new JwtRequest("admin", "admin");

        MvcResult result =
                mvc.perform(post("/auth")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(requestBody)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.token").isNotEmpty())
                        .andReturn();

        JwtResponse response =
                mapper.readValue(result.getResponse().getContentAsByteArray(),
                        JwtResponse.class);

        return cachedAdminToken = response.getToken();
    }

    /** Convenience wrapper so you can write <code>.with(bearer())</code>. */
    protected RequestPostProcessor bearer() throws Exception {
        String token = adminToken();
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
}
