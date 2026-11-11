package com.henrique.dashboard.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain security(HttpSecurity http, @Value("${dashboard.api-key:}") String apiKey, JwtDecoder jwtDecoder) throws Exception {
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String supplied = request.getHeader("X-API-Key");
                if (SecurityContextHolder.getContext().getAuthentication() == null && expected.length > 0 && supplied != null
                        && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new UsernamePasswordAuthenticationToken("api-reader", null, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_API_READER"))));
                    SecurityContextHolder.setContext(context);
                }
                chain.doFilter(request, response);
            }
        };
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll()
                        .requestMatchers("/v1/admin/users/**").hasAuthority("SCOPE_admin:users")
                        .requestMatchers(HttpMethod.POST, "/v1/collections").hasAuthority("SCOPE_collections:write")
                        .requestMatchers(HttpMethod.GET, "/v1/collections", "/v1/collections/**").hasAuthority("SCOPE_collections:write")
                        .requestMatchers(HttpMethod.GET, "/v1/items", "/v1/items/**").hasAnyAuthority("ROLE_API_READER", "SCOPE_items:read")
                        .requestMatchers(HttpMethod.DELETE, "/v1/items/**").hasAuthority("SCOPE_items:delete")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.sendError(401)))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
