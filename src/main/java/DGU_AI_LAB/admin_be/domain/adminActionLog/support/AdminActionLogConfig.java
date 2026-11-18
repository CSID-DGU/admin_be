package DGU_AI_LAB.admin_be.domain.adminActionLog.support;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminActionLogConfig implements WebMvcConfigurer {

    private final AdminActionLogInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 어노테이션이 붙은 메서드만 기록하지만, 관리자 API 밖은 아예 들여다보지 않는다.
        registry.addInterceptor(interceptor).addPathPatterns("/api/admin/**");
    }
}
