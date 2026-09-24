package com.connectly.config;

import com.connectly.security.JwtAuthFilter;
import com.connectly.security.JwtProperties;
import com.connectly.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtProperties props;
    private final RestAuthenticationEntryPoint entryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, JwtProperties props, RestAuthenticationEntryPoint entryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
        this.entryPoint = entryPoint;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // bearer-token API; no cookie auth → CSRF not applicable
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .addHeaderWriter((request, response) -> {
                            // Static CSP that allows the SPA assets and inline React styles.
                            // Tighten (nonces/hashes) when the frontend build pipeline is final.
                            response.setHeader("Content-Security-Policy",
                                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                            + "script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
                            response.setHeader("X-Content-Type-Options", "nosniff");
                            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                            response.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=()");
                        }))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        // public surface
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/verify-email",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/oauth/google/callback",
                                "/api/v1/auth/oauth/status").permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**", "/error").permitAll()
                        // WebSocket handshake: STOMP CONNECT itself must present a valid
                        // access token (enforced in WebSocketConfig) and subscriptions are
                        // restricted to the user's own topic, so this stays safe.
                        .requestMatchers("/ws/chat", "/ws/chat/**").permitAll()
                        // public social surface (read-only)
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/explore",
                                "/api/v1/posts/*/comments").permitAll()
                        // uploaded media files are public, read-only, generated names
                        .requestMatchers(HttpMethod.GET, "/media/*").permitAll()
                        // open voice-room list is public read-only; joining still requires auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/voice/rooms").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // admin surface — role enforced here AND via method security
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // everything else needs a valid access token
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = props.cors() != null && props.cors().allowedOrigins() != null
                ? props.cors().allowedOrigins()
                : List.of("http://localhost:5173");
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
