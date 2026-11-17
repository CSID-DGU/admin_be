package DGU_AI_LAB.admin_be.domain.adminActionLog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 이 엔드포인트가 성공하든 실패하든 관리자 작업 기록을 하나 남긴다. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAction {
    /** 무슨 작업인지 */
    AdminActionType value();

    /** 대상의 식별자를 담은 경로 변수 이름. 예: "requestId" */
    String targetId();
}
