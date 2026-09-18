package com.leadrat.crm.leads.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stateless JWT resource server, validating AWS Cognito tokens.
 *
 * <p>The issuer is configured in {@code application.yaml}; Spring fetches the pool JWKS from it
 * on first use, so an unset pool id means no token validates.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    /**
     * Where the browser is allowed to call this API from when nothing is configured.
     *
     * <p>The default lives here rather than in {@code application.yaml} because an empty
     * allow-list is not a safe fallback: it rejects every request that carries an {@code Origin},
     * and browsers attach one to same-origin POST/PUT/PATCH/DELETE even though they omit it on
     * same-origin GET. A blank {@code APP_CORS_ORIGINS} therefore reads as "the site loads but
     * nothing can be saved", with a bare 403 and no clue why. Defaulting in code means the
     * variable can be unset, or blanked by a host {@code .env} being rewritten, without that
     * happening.
     *
     * <p>These are matched as patterns, so Vercel's per-deployment hostnames
     * ({@code leads-crm-frontend-<hash>-<scope>.vercel.app}) are covered by the same entry as the
     * production one. Pinning exact origins would 403 every preview deploy.
     */
    private static final List<String> DEFAULT_ALLOWED_ORIGIN_PATTERNS = List.of(
            // Vite picks the next free port when one is taken, so the usual range is allowed.
            "http://localhost:517*", "http://127.0.0.1:517*",
            // Production and every preview/branch deployment of the frontend.
            "https://leads-crm-frontend-*.vercel.app");

    private final List<String> allowedOriginPatterns;

    public WebSecurityConfig(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        List<String> configured = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        if (configured.isEmpty()) {
            log.info("app.cors.allowed-origins is not set; allowing the built-in defaults {}",
                    DEFAULT_ALLOWED_ORIGIN_PATTERNS);
            this.allowedOriginPatterns = DEFAULT_ALLOWED_ORIGIN_PATTERNS;
        } else {
            this.allowedOriginPatterns = configured;
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-resources/**", "/webjars/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Patterns rather than exact origins: setAllowedOriginPatterns accepts a literal origin
        // unchanged, so this stays a superset of the old behaviour while also matching the
        // wildcard hosts Vercel generates per deployment.
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Maps Cognito groups onto Spring authorities, and names {@code cognito:username} as the
     * principal so {@code @CreatedBy} records a person rather than a UUID.
     *
     * <p>Groups arrive bare, not ROLE_-prefixed, which is why {@code TenantAware} accepts both
     * spellings.
     */
    @SuppressWarnings("unchecked")
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("cognito:username");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> groups =
                    (List<String>) jwt.getClaims().getOrDefault("cognito:groups", Collections.emptyList());
            if (groups == null || groups.isEmpty()) {
                return List.of(new SimpleGrantedAuthority("ROLE_UNKNOWN"));
            }
            return groups.stream()
                    .map(group -> (GrantedAuthority) new SimpleGrantedAuthority(group))
                    .toList();
        });
        return converter;
    }
}
