package DGU_AI_LAB.admin_be.domain.adminActionLog;

/** 이 엔드포인트가 성공하든 실패하든 관리자 작업 기록을 하나 남긴다. */
@Target(ElementType.METHOD)          // 메서드에만 붙일 수 있다. 클래스나 필드에 붙이면 컴파일 오류.
@Retention(RetentionPolicy.RUNTIME) // 실행 중에도 남겨 둔다. 빼면 기본값 CLASS가 되어 실행 중에 읽을 수 없고,
// 기록 코드가 항상 null을 받아 아무것도 남지 않는다(오류도 안 난다).
public @interface AdminAction {
    /** 무슨 작업인지 */
    AdminActionType value();
    /** 대상의 식별자를 담은 경로 변수 이름. 예: "requestId" */
    String targetId();
}