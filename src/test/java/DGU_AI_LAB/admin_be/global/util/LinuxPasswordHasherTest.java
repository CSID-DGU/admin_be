package DGU_AI_LAB.admin_be.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinuxPasswordHasherTest {

    // config-server 요청 검증·이미지 entrypoint.sh의 USER_PW_HASH_RE와 같은 규칙
    private static final String SHA512_CRYPT = "^\\$6\\$[./0-9A-Za-z]{16}\\$[./0-9A-Za-z]{86}$";

    @Test
    @DisplayName("glibc crypt(3)와 같은 값을 만든다 — /etc/shadow와 chpasswd -e가 그대로 받는다")
    void matchesGlibcSha512Crypt() {
        // 기대값은 `openssl passwd -6 -salt <salt> <password>` 출력
        assertThat(LinuxPasswordHasher.sha512Crypt("Hello world!", "saltstring"))
                .isEqualTo("$6$saltstring$svn8UoSVapNtMuq1ukKS4tPQd8iKwSMHWjl/O817G3uBnIFNjnQJuesI68u4OTLiBFdcbYEdFCoEOfaS35inz1");
        assertThat(LinuxPasswordHasher.sha512Crypt("비밀번호:Pw1!", "Ab1./Cd2Ef3Gh4Ij"))
                .isEqualTo("$6$Ab1./Cd2Ef3Gh4Ij$vJx9dhHKJeMrw.888u1DC0Gw3sAmA6m.G2te5aETwYZqs2/Jg9B3FYua/Y/2XtsMUa/S/Hc7X770.sST4O4Fu/");
    }

    @Test
    @DisplayName("매번 새 16자 salt를 써서, 같은 비밀번호도 해시가 다르고 평문이 드러나지 않는다")
    void usesFreshSaltAndHidesPlaintext() {
        String first = LinuxPasswordHasher.sha512Crypt("strongPassword1!");
        String second = LinuxPasswordHasher.sha512Crypt("strongPassword1!");

        assertThat(first).matches(SHA512_CRYPT).doesNotContain("strongPassword1!");
        assertThat(second).matches(SHA512_CRYPT);
        assertThat(first).isNotEqualTo(second);
    }
}
