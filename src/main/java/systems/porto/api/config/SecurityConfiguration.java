package systems.porto.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import systems.porto.api.auth.AccessTokenService;
import systems.porto.api.auth.BearerAuthenticationFilter;
import systems.porto.api.plugin.PluginRuntime;

@Configuration
public class SecurityConfiguration {

    private final PortoApiProperties portoApiProperties;

    public SecurityConfiguration(final PortoApiProperties portoApiProperties) {
        this.portoApiProperties = portoApiProperties;
    }

    @Bean
    BearerAuthenticationFilter bearerAuthenticationFilter(
        final PluginRuntime pluginRuntime,
        final AccessTokenService accessTokenService
    ) {
        return new BearerAuthenticationFilter(portoApiProperties, pluginRuntime, accessTokenService);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        final HttpSecurity http,
        final BearerAuthenticationFilter bearerAuthenticationFilter
    ) throws Exception {
        String apiBasePath = portoApiProperties.getApiBasePath();
        boolean authRequired = portoApiProperties.getAuth().isRequired();
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(
                    "/error",
                    "/actuator/health",
                    "/actuator/info",
                    "/api/docs",
                    "/api/docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    apiBasePath + "/health",
                    apiBasePath + "/auth/token",
                    "/h2-console/**"
                ).permitAll();
                if (authRequired) {
                    auth.anyRequest().authenticated();
                } else {
                    auth.requestMatchers(apiBasePath + "/**").permitAll()
                        .anyRequest().authenticated();
                }
            })
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
