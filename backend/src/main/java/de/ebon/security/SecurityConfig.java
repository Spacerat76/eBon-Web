package de.ebon.security;

import de.ebon.api.error.ApiErrorFactory;
import de.ebon.config.AppOpenApiProperties;
import de.ebon.config.AppSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties({AppSecurityProperties.class, AppOpenApiProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppSecurityProperties securityProperties,
            AppOpenApiProperties openApiProperties,
            ApiErrorFactory apiErrorFactory,
            ObjectMapper objectMapper) throws Exception {
        ApiTokenAuthenticationFilter apiTokenFilter = new ApiTokenAuthenticationFilter(securityProperties);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(apiErrorFactory, objectMapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(apiErrorFactory, objectMapper)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/health").permitAll();
                    if (openApiProperties.publicAccess()) {
                        auth.requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                                .permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
