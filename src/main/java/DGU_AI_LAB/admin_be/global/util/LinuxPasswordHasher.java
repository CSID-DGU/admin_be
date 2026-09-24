package DGU_AI_LAB.admin_be.global.util;

import org.apache.commons.codec.digest.Sha2Crypt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 우분투 계정 로그인 비밀번호를 /etc/shadow에 그대로 쓰는 SHA-512 crypt 형식($6$salt$hash)으로 만든다.
 * 평문은 신청을 받는 순간 여기서 해시로 바꾸고 어디에도 남기지 않는다 — config-server와 컨테이너
 * (chpasswd -e)는 이 해시만 받는다.
 */
public final class LinuxPasswordHasher {

    private static final String SALT_ALPHABET = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LinuxPasswordHasher() {
    }

    public static String sha512Crypt(String plaintext) {
        return sha512Crypt(plaintext, randomSalt());
    }

    static String sha512Crypt(String plaintext, String salt) {
        return Sha2Crypt.sha512Crypt(plaintext.getBytes(StandardCharsets.UTF_8), "$6$" + salt);
    }

    private static String randomSalt() {
        StringBuilder salt = new StringBuilder(SALT_LENGTH);
        for (int i = 0; i < SALT_LENGTH; i++) {
            salt.append(SALT_ALPHABET.charAt(RANDOM.nextInt(SALT_ALPHABET.length())));
        }
        return salt.toString();
    }
}
