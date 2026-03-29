package DGU_AI_LAB.admin_be.support;

import DGU_AI_LAB.admin_be.global.auth.CustomUserDetailsService;
import DGU_AI_LAB.admin_be.global.auth.jwt.JwtProvider;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * @WebMvcTest에서 Spring Security 및 JPA 관련 빈 충돌 방지를 위한 공통 Mock 설정.
 * - SecurityConfig → JwtProvider, CustomUserDetailsService, RedisTemplate 필요
 * - spring-data-jpa 클래스패스 존재 시 JpaMetamodelMappingContext 필요
 */
public abstract class WebMvcTestSupport {

    @MockitoBean
    protected JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    protected JwtProvider jwtProvider;

    @MockitoBean
    protected CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    protected RedisTemplate<String, String> redisTemplate;
}
