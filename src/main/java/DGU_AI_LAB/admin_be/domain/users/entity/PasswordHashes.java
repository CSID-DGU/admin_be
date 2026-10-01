package DGU_AI_LAB.admin_be.domain.users.entity;

import DGU_AI_LAB.admin_be.global.util.LinuxPasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 하나에서 만든 두 해시. 웹 비밀번호가 곧 SSH(Ubuntu) 비밀번호라 항상 함께 만들고 함께 바꾼다.
 *
 * @param web    웹 로그인용(BCrypt)
 * @param ubuntu 리눅스 계정용(SHA-512 crypt). 컨테이너의 /etc/shadow에 그대로 들어간다
 */
public record PasswordHashes(String web, String ubuntu) {

    public static PasswordHashes of(String rawPassword, PasswordEncoder passwordEncoder) {
        return new PasswordHashes(passwordEncoder.encode(rawPassword), LinuxPasswordHasher.sha512Crypt(rawPassword));
    }

    /** 해시가 로그에 찍히지 않게 한다. */
    @Override
    public String toString() {
        return "PasswordHashes[***]";
    }
}
