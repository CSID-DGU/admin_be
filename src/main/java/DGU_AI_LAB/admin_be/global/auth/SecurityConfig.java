package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.global.auth.jwt.JwtAccessDeniedHandler;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtAuthenticationEntryPoint;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtAuthenticationFilter;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import DGU_AI_LAB.admin_be.global.config.CorsConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@RequiredArgsConstructor
@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsConfig corsConfig;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${config.api-token:}")
    private String internalApiToken;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(config -> config
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityWhitelist.UNPROTECTED_PATHS.toArray(new String[0])).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // health·info 말고는 운영 정보(지표·환경 값)라, 설정에서 노출 대상이 늘어도 관리자만 본다.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilter(corsConfig.corsFilter())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, customUserDetailsService, redisTemplate),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(new ExceptionHandlerFilter(), JwtAuthenticationFilter.class)
                .addFilterBefore(new InternalTokenFilter(internalApiToken), ExceptionHandlerFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
