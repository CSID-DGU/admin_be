package DGU_AI_LAB.admin_be.global.validation;

/** 전화번호를 저장용 한 가지 모양(010-1234-5678, 02-123-4567)으로 맞춘다. */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /**
     * 숫자만 뽑아 지역번호-국번-번호로 나눈다. 서울(02)만 앞자리가 두 자리다.
     * 모양을 알 수 없는 값은 손대지 않고 앞뒤 공백만 뗀다 — 형식 검사는 {@link PhoneNumber}가 맡는다.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        int areaLength = digits.startsWith("02") ? 2 : 3;
        int restLength = digits.length() - areaLength;
        if (!digits.startsWith("0") || digits.startsWith("00") || restLength < 7 || restLength > 8) {
            return raw.strip();
        }
        int middleEnd = digits.length() - 4;
        return digits.substring(0, areaLength) + "-" + digits.substring(areaLength, middleEnd) + "-" + digits.substring(middleEnd);
    }
}
