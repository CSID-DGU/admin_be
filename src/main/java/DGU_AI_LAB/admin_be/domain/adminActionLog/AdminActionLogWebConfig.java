package DGU_AI_LAB.admin_be.domain.adminActionLog;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AdminActionLogWebConfig implements WebMvcConfigurer {

    private final AdminActionLogInterceptor adminActionLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminActionLogInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}