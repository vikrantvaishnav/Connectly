package com.connectly.security;

import com.connectly.user.User;
import com.connectly.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final com.connectly.presence.PresenceService presence;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository,
                         com.connectly.presence.PresenceService presence) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.presence = presence;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            jwtService.parse(token, JwtService.TYPE_ACCESS).ifPresent(claims -> {
                Long userId = Long.valueOf(claims.getSubject());
                userRepository.findById(userId).ifPresent(user -> {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // Any authenticated request refreshes the user's "active now" status.
                    presence.touch(userId);
                    // expose the session id for handlers that need "which device is this?"
                    Object sid = claims.get(JwtService.CLAIM_SESSION);
                    if (sid instanceof Number n) {
                        request.setAttribute("sessionId", n.longValue());
                    }
                });
            });
        }
        chain.doFilter(request, response);
    }
}
