//package net.blueshell.common.auth;
//
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.List;
//
//@Component
//public class XHeaderAuthenticationFilter extends OncePerRequestFilter {
//
//    @Override
//    protected void doFilterInternal(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            FilterChain filterChain)
//            throws ServletException, IOException {
//
//        String userId = request.getHeader("X-User-Id");
//        String username = request.getHeader("X-User-Name");
//        String roles = request.getHeader("X-User-Roles");
//
//        if (username != null) {
//            // Convert "ADMIN,INTERNAL" to [ROLE_ADMIN, ROLE_INTERNAL]
//            List<SimpleGrantedAuthority> authorities = parseRoles(roles);
//
//            UsernamePasswordAuthenticationToken auth =
//                    new UsernamePasswordAuthenticationToken(
//                            username,    // principal name
//                            null,
//                            authorities
//                    );
//            SecurityContextHolder.getContext().setAuthentication(auth);
//        }
//        filterChain.doFilter(request, response);
//    }
//
//    private List<SimpleGrantedAuthority> parseRoles(String roles) {
//        return Arrays.stream(roles.split(",")).map((role) -> new GrantedAuthority(role) {
//        }).toList();
//    }
//}
