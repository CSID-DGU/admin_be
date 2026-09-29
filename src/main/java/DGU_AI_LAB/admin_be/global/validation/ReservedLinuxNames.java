package DGU_AI_LAB.admin_be.global.validation;

/**
 * 새 우분투 계정명으로 받지 않는 리눅스 계정·그룹 이름(이미지 그룹, 원장 시드 계정, 운영용 계정).
 * 이 이름으로 우분투 계정을 만들면 원장에 같은 이름의 사용자가 있거나(USER_ALREADY_EXISTS),
 * 개인 그룹이 이미지 그룹과 부딪혀(primary group conflict) 승인 뒤에야 계정 생성이 실패한다 —
 * 등록 시점에 막는다.
 *
 * <p>목록의 원본은 config-server({@code GET /reserved-names})다. 이미지를 바꿀 때 한 곳만 고치면 된다.
 */
public interface ReservedLinuxNames {

    boolean contains(String name);
}
