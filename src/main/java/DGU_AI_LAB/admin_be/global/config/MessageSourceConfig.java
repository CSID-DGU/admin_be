package DGU_AI_LAB.admin_be.global.config;

import DGU_AI_LAB.admin_be.domain.messageTemplate.repository.MessageTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.text.MessageFormat;
import java.util.Locale;

@Configuration
@RequiredArgsConstructor
public class MessageSourceConfig {

    private final MessageTemplateRepository messageTemplateRepository;

    @Bean
    @Primary
    public MessageSource dbMessageSource() {
        // ponytail: no cache — alarm-only traffic, add @Cacheable if it matters
        AbstractMessageSource dbSource = new AbstractMessageSource() {
            @Override
            protected MessageFormat resolveCode(String code, Locale locale) {
                return messageTemplateRepository.findById(code)
                        .map(t -> new MessageFormat(t.getValue(), locale))
                        .orElse(null);
            }
        };

        ResourceBundleMessageSource fallback = new ResourceBundleMessageSource();
        fallback.setBasename("messages");
        fallback.setDefaultEncoding("UTF-8");

        dbSource.setParentMessageSource(fallback);
        return dbSource;
    }
}
