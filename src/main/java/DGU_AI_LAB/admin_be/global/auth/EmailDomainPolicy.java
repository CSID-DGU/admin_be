package DGU_AI_LAB.admin_be.global.auth;

import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 가입(인증 메일 발송·회원가입)을 허용하는 이메일 도메인. 학교 구성원만 GPU 서버를 신청하도록 하고, 인증 메일이
 * 아무 주소로나 나가지 않게 한다. 도메인은 정확히 일치해야 한다(하위 도메인 불가).
 */
@Component
public class EmailDomainPolicy {

    private final List<String> allowedDomains;

    public EmailDomainPolicy(@Value("${auth.allowed-email-domains:dgu.ac.kr,dongguk.edu}") List<String> allowedDomains) {
        this.allowedDomains = allowedDomains.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
    }

    public void requireAllowed(String email) {
        if (!isAllowed(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
    }

    boolean isAllowed(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        return allowedDomains.contains(email.substring(at + 1).toLowerCase(Locale.ROOT));
    }
}
