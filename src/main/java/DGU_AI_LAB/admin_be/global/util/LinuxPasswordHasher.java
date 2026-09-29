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

    /**
     * 반복 횟수. glibc 기본값 5000은 GPU 대입에 BCrypt(cost 10)보다 수백 배 약하다 — 같은 비밀번호가 웹 로그인에도
     * 쓰이므로 이 해시가 가장 약한 고리가 된다. passlib의 sha512_crypt 기본값을 따른다.
     */
    static final int ROUNDS = 656_000;
    private static final String CURRENT_PREFIX = "$6$rounds=" + ROUNDS + "$";
    private static final String SALT_ALPHABET = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LinuxPasswordHasher() {
    }

    public static String sha512Crypt(String plaintext) {
        return crypt(plaintext, CURRENT_PREFIX + randomSalt());
    }

    /** 지금 반복 횟수로 만든 해시인지. 아니면(옛 5000회 해시 등) 평문을 볼 수 있을 때 다시 만든다. */
    public static boolean isCurrentStrength(String hash) {
        return hash != null && hash.startsWith(CURRENT_PREFIX);
    }

    public static String currentPrefix() {
        return CURRENT_PREFIX;
    }

    /** setting은 crypt(3)의 "$6$[rounds=N$]salt" 부분. */
    static String crypt(String plaintext, String setting) {
        return Sha2Crypt.sha512Crypt(plaintext.getBytes(StandardCharsets.UTF_8), setting);
    }

    private static String randomSalt() {
        StringBuilder salt = new StringBuilder(SALT_LENGTH);
        for (int i = 0; i < SALT_LENGTH; i++) {
            salt.append(SALT_ALPHABET.charAt(RANDOM.nextInt(SALT_ALPHABET.length())));
        }
        return salt.toString();
    }
}
