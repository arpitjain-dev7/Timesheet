package com.timesheetManagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils               jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path  = request.getRequestURI();
        String method = request.getMethod();

        String token = extractTokenFromRequest(request);

        if (!StringUtils.hasText(token)) {
            log.trace("[JWT_FILTER] No Bearer token on {} {}", method, path);
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtils.validateToken(token)) {
            log.warn("[JWT_FILTER] Invalid or expired token on {} {}", method, path);
            filterChain.doFilter(request, response);
            return;
        }

        String username = jwtUtils.extractUsername(token);
        log.debug("[JWT_FILTER] Valid token for username='{}' on {} {}", username, method, path);

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtils.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("[JWT_FILTER] Authenticated username='{}', roles={} for {} {}",
                        username, userDetails.getAuthorities(), method, path);
            } else {
                log.warn("[JWT_FILTER] Token failed isTokenValid check for username='{}' on {} {}",
                        username, method, path);
            }
        } catch (UsernameNotFoundException ex) {
            log.warn("[JWT_FILTER] User '{}' in token not found in DB — {} {}",
                    username, method, path);
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
