// com/gigshield/auth/filter/JwtFilter.java
package com.gigshield.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gigshield.auth.util.JwtUtil;
import com.gigshield.config.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil             jwtUtil;
    private final UserDetailsService  userDetailsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AppConstants.JWT_HEADER);

        if (!StringUtils.hasText(authHeader)
                || !authHeader.startsWith(AppConstants.JWT_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token    = authHeader.substring(AppConstants.JWT_PREFIX.length());
        String username = null;

        try {
            username = jwtUtil.extractUsername(token);
        } catch (Exception e) {
            log.warn("Could not extract username from JWT: {}", e.getMessage());
        }

        if (username == null) {
            chain.doFilter(request, response);
            return;
        }

        // ── Ban check — O(1) Redis lookup before any DB call ─────────────────
        if (isBanned(username)) {
            log.warn("Banned user attempted access: {}", username);
            writeErrorResponse(response,
                    HttpStatus.FORBIDDEN,
                    "Account permanently suspended");
            return;    // do NOT continue the filter chain
        }

        // ── Normal JWT authentication ─────────────────────────────────────────
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (jwtUtil.isTokenValid(token)) {
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        chain.doFilter(request, response);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean isBanned(String phone) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey("ban:" + phone));
        } catch (Exception e) {
            // Redis unavailable — fail open to avoid locking out all users,
            // but log loudly so ops is alerted
            log.error("Redis ban-check failed for user {}. Failing open: {}",
                    phone, e.getMessage());
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response,
                                    HttpStatus status,
                                    String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "status",    status.value(),
                "error",     status.getReasonPhrase(),
                "message",   message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}