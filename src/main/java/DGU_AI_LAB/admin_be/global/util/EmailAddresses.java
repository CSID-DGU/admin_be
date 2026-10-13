package DGU_AI_LAB.admin_be.global.util;

import java.util.Locale;

/** 이메일 주소를 키나 로그에 쓸 때의 표기를 한 곳에서 정한다. */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    /** DB가 이메일 대소문자를 구분하지 않으므로, Redis 키도 같은 주소면 같은 키가 되게 한 표기로 맞춘다. */
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 로그·경보에 남길 표기. 첫 글자와 도메인만 남긴다. */
    public static String mask(String email) {
        if (email == null) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
