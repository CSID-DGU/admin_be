package DGU_AI_LAB.admin_be.domain.adminActionLog.entity;


import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminActionType;
import DGU_AI_LAB.admin_be.domain.adminActionLog.AdminTargetType;
import DGU_AI_LAB.admin_be.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;




@Entity
@Getter
@Table(name = "admin_action_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_action_log_id")
    private Long actionLogId;

    @Column(name = "action_by" , nullable = false)
    private Long actionBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type" , nullable = false, length = 50)
    private  AdminActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type" , nullable = false, length = 30)
    private AdminTargetType targetType;

    @Column(name = "target_id" , nullable = false, length = 100)
    private String targetId;

    @Column(name = "result" , nullable = false, length = 20)
    private String result;

    @Column(name = "changed_value")
    private String changedValue;

    public AdminActionLog(Long actionBy, AdminActionType actionType, String targetId, String result, String changedValue) {
        this.actionBy = actionBy;
        this.actionType = actionType;
        this.targetType = actionType.targetType();   // 대상 종류는 작업 종류에서 꺼냄
        this.targetId = targetId;
        this.result = result;
        this.changedValue = changedValue;
    }

}
