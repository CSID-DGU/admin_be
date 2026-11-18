package DGU_AI_LAB.admin_be.domain.adminActionLog;

public enum AdminActionType {
    // 신청 — /api/admin/requests/{requestId}/...
    REQUEST_APPROVE(AdminTargetType.REQUEST),
    REQUEST_REJECT(AdminTargetType.REQUEST),
    REQUEST_MIGRATE(AdminTargetType.REQUEST),
    REQUEST_RESTART(AdminTargetType.REQUEST),
    CONTAINER_DELETE(AdminTargetType.REQUEST),
    CONTAINER_FORCE_COMPLETE(AdminTargetType.REQUEST),

    // 사용자 — /api/admin/users/{id}/... , /api/admin/users/{userId}/warnings...
    USER_ACTIVATION_CHANGE(AdminTargetType.USER),
    USER_ROLE_CHANGE(AdminTargetType.USER),
    USER_PASSWORD_RESET(AdminTargetType.USER),
    USER_GROUP_REMOVE(AdminTargetType.USER),
    UBUNTU_ACCOUNT_DELETE(AdminTargetType.USER),
    WARNING_GRANT(AdminTargetType.USER),
    WARNING_DEDUCT(AdminTargetType.USER),
    WARNING_CANCEL(AdminTargetType.USER),

    // 변경 신청 — /api/admin/change-requests/{changeRequestId}/...
    CHANGE_REQUEST_APPROVE(AdminTargetType.CHANGE_REQUEST),
    CHANGE_REQUEST_REJECT(AdminTargetType.CHANGE_REQUEST),

    // Pod — /api/admin/pods/{podName}
    ORPHAN_POD_DELETE(AdminTargetType.POD),

    // 그룹 탈퇴 신청 — /api/admin/group-leave-requests/{leaveRequestId}/...
    GROUP_LEAVE_APPROVE(AdminTargetType.GROUP_LEAVE_REQUEST),
    GROUP_LEAVE_REJECT(AdminTargetType.GROUP_LEAVE_REQUEST);

    private final AdminTargetType targetType;

    AdminActionType(AdminTargetType targetType) {
        this.targetType = targetType;
    }

    public AdminTargetType targetType() {
        return targetType;
    }
}
