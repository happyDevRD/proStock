package com.happydev.prestockbackend.config;


import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.repository.UserRepository;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.LoginRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    private final UserRepository userRepository;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LoginRateLimitFilter loginRateLimitFilter;

    public SecurityConfig(
            UserRepository userRepository,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            LoginRateLimitFilter loginRateLimitFilter
    ) {
        this.userRepository = userRepository;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.loginRateLimitFilter = loginRateLimitFilter;
    }

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Value("${app.security.allow-swagger:true}")
    private boolean allowSwagger;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String[] swaggerMatchers = allowSwagger
                ? new String[]{"/swagger-ui/**", "/v3/api-docs/**"}
                : new String[0];

        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                        .contentTypeOptions(withDefaults())
                )
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(swaggerMatchers).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/portal/auth/login").permitAll()
                        .requestMatchers("/api/users/**").hasAnyAuthority("ROLE_GESTOR", "users.manage")
                        .requestMatchers("/api/audit-logs/**").hasAnyAuthority("ROLE_GESTOR", "audit.view")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "GESTOR")
                        .requestMatchers(HttpMethod.GET, "/api/modules/flags", "/api/features").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/modules/flags", "/api/features")
                            .hasAnyAuthority("ROLE_GESTOR", "settings.manage_features")
                        .requestMatchers(HttpMethod.PUT, "/api/permissions/role-matrix")
                            .hasAnyAuthority("ROLE_GESTOR", "settings.manage_permissions")
                        .requestMatchers("/api/permissions/users/**")
                            .hasAnyAuthority("ROLE_GESTOR", "settings.manage_permissions")
                        .requestMatchers("/api/accounting/**").authenticated()
                        .requestMatchers("/api/service-orders/**")
                            .hasAnyAuthority("ROLE_GESTOR", "ROLE_ADMIN", "view.service_orders")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            com.happydev.prestockbackend.entity.User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
            UserRole role = user.getRole() != null ? user.getRole() : UserRole.USER;
            return User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(role.name())
                    .build();
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
