package DGU_AI_LAB.admin_be.domain.groups.entity;

/** 그룹 탈퇴 신청의 상태. PENDING → APPROVED(제거 작업 등록) 또는 REJECTED. */
public enum GroupLeaveStatus {
    PENDING,
    APPROVED,
    REJECTED
}
