package DGU_AI_LAB.admin_be.global.util;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MessageUtils {

    private final MessageSource messageSource;

    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, Locale.KOREA);
    }
}