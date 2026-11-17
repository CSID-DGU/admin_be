package DGU_AI_LAB.admin_be.domain.adminActionLog.web;

import DGU_AI_LAB.admin_be.domain.adminActionLog.service.AdminActionLogDispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminActionLogWebConfig {
    @Bean
    public AdminActionInterceptor adminActionInterceptor(
            AdminActionLogDispatcher dispatcher, ObjectMapper mapper) {
        return new AdminActionInterceptor(dispatcher, mapper);
    }

    @Bean
    public WebMvcConfigurer adminActionLogConfigurer(AdminActionInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/api/admin/**");
            }
        };
    }
}
