package com.example.tgs_dev.security;

import com.example.tgs_dev.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.validateAndExtract(token);

            if (!"access".equals(claims.get("type", String.class))) {
                sendError(response, "Token type not allowed");
                return;
            }

            String username = claims.getSubject();

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Populate the tenant context so service-layer queries can scope
                // results to this user's company without extra DB lookups.
                // SUPER_ADMIN users have no tenant company; guard before setting.
                if (userDetails instanceof User u) {
                    Optional.ofNullable(u.getCompany())
                            .ifPresent(c -> TenantContext.set(c.getId()));
                }
            }

        } catch (ExpiredJwtException e) {
            sendError(response, "Token expired");
            return;
        } catch (JwtException e) {
            sendError(response, "Invalid token");
            return;
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Always clear the tenant context to prevent context leakage on
            // thread-pool threads that serve subsequent requests.
            TenantContext.clear();
        }
    }

    private void sendError(HttpServletResponse res, String msg) throws IOException {
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.getWriter().write("""
                {
                  "success": false,
                  "message": "%s",
                  "data": null,
                  "errors": [],
                  "timestamp": "%s"
                }""".formatted(msg, LocalDateTime.now()));
    }
}
