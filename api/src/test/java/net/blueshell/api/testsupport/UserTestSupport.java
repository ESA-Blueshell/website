package net.blueshell.api.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.blueshell.api.controller.request.JwtRequest;
import net.blueshell.api.controller.response.JwtResponse;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.User;
import net.blueshell.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test support for creating users with arbitrary roles and obtaining their JWT tokens.
 */
public abstract class UserTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "Password123!";

    /**
     * Create and persist a new user with the given role.
     * The username is generated uniquely per test run.
     */
    protected User createUserWithRole(Role role) {
        String username = role.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User(username, passwordEncoder.encode(DEFAULT_PASSWORD), "Test", "User", username + "@example.com");
        user.setEnabled(true);
        user.addRole(role);
        return userRepository.save(user);
    }

    /**
     * Generate a JWT token for a newly created user with the given role.
     */
    protected String tokenForRole(Role role) throws Exception {
        User user = createUserWithRole(role);
        JwtRequest requestBody = new JwtRequest(user.getUsername(), DEFAULT_PASSWORD);

        MvcResult result = mvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(requestBody))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();

        JwtResponse response = mapper.readValue(result.getResponse().getContentAsByteArray(), JwtResponse.class);
        return response.getToken();
    }


    protected String tokenForUser(User user) throws Exception {
        JwtRequest requestBody = new JwtRequest(user.getUsername(), DEFAULT_PASSWORD);
        MvcResult result = mvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(requestBody))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        JwtResponse response = mapper.readValue(result.getResponse().getContentAsByteArray(), JwtResponse.class);
        return response.getToken();
    }

    /**
     * Convenience wrapper so you can write .with(bearer(role)).
     */
    protected RequestPostProcessor bearer(Role role) throws Exception {
        String token = tokenForRole(role);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    protected RequestPostProcessor bearer(User user) throws Exception {
        String token = tokenForUser(user);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /**
     * Set the SecurityContext to a user with the given role for non-MVC tests.
     */
    protected void setAuthenticationWithRole(Role role) {
        User user = createUserWithRole(role);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    protected User refreshUser(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }
}
