package net.blueshell.common.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.enums.Role;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads X-User-Id, X-User-Name and X-User-Roles (comma‑sep) headers
 * and, if present, constructs a UserDetailsDTO + authorities and
 * injects it into the SecurityContext.
 */
@Component
@Slf4j
public class IdentityFilter extends OncePerRequestFilter {

    public static final String HEADER_ID = "X-User-Id";
    public static final String HEADER_NAME = "X-User-Name";
    public static final String HEADER_ROLES = "X-User-Roles";

    private static final AntPathRequestMatcher USER_DETAILS = new AntPathRequestMatcher("/auth/identity");

    @Override
    protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
        // don't run this filter on /auth/identity
        return USER_DETAILS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain) throws ServletException, IOException {
        String idHeader = request.getHeader(HEADER_ID);
        String nameHeader = request.getHeader(HEADER_NAME);
        String rolesHeader = request.getHeader(HEADER_ROLES);
        log.info("IdentityFilter with request type: {}", request.getMethod());
        log.info("IDentity filter request path: {}", request.getRequestURI());

        if (idHeader != null && nameHeader != null && rolesHeader != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {
            Long id = Long.valueOf(idHeader);
            String username = nameHeader;
            Set<Role> roles = Arrays.stream(rolesHeader.split(",")).map(Role::valueOf).collect(Collectors.toSet());

            // build your DTO
            Identity user = new Identity();
            user.setId(id);
            user.setUsername(username);
            user.setRoles(roles);

            // flatten inherited roles into GrantedAuthority
            List<SimpleGrantedAuthority> authorities = roles.stream().map(r -> new SimpleGrantedAuthority(r.toString()))
                    .collect(Collectors.toList());

            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.info("IdentityFilter identity successfully authenticated");
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Identity anonUser = new Identity();
            anonUser.setRoles(Collections.singleton(Role.ANONYMOUS));
            anonUser.setUsername("anonymous");
            anonUser.setId(0L);

            List<SimpleGrantedAuthority> anonAuth = List.of(new SimpleGrantedAuthority(Role.ANONYMOUS.toString()));
            AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken("coolBeans", anonUser, anonAuth);
            SecurityContextHolder.getContext().setAuthentication(anonymousToken);
            log.info("IdentityFilter anonymously authenticated");
        }

        chain.doFilter(request, response);
    }
}
