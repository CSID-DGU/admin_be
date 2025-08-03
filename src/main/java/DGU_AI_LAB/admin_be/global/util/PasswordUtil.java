package DGU_AI_LAB.admin_be.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class PasswordUtil {
    private PasswordUtil() {}

    public static String encodePassword(String passwordBase64) {
        try {
            // sha256 해싱
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(passwordBase64.getBytes(StandardCharsets.UTF_8));

            // byte[] -> hex
            StringBuilder passwordHex = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) passwordHex.append('0');
                passwordHex.append(hex);
            }

            return passwordHex.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}