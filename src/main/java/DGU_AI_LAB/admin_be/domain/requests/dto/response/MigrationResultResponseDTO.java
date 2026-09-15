package DGU_AI_LAB.admin_be.domain.requests.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청의 마지막 마이그레이션 작업 결과. 화면이 마이그레이션 등록 뒤 신청 상태가 FULFILLED로 돌아오면 이 값으로
 * 옮겼는지·건너뛰었는지·실패했는지를 보여 준다. 작업 결과의 자원 정보는 하루 뒤 사라지고 phase만 남는다.
 */
public record MigrationResultResponseDTO(
        @Schema(description = "none / START / SUCCESS / FAIL / UNKNOWN") String phase,
        @Schema(description = "실패 원인 코드") String errorCode,
        @Schema(description = "migrated / skipped (성공한 작업만)") String status,
        @Schema(description = "건너뛴 이유: no_candidate_node / no_significant_improvement") String reason,
        String fromNode,
        String toNode,
        String podName,
        @Schema(description = "기존 Pod 정리에 실패했으면 failed") String oldPodCleanup,
        String updatedAt
) {
    public static MigrationResultResponseDTO from(JobResultResponseDTO job) {
        JobResultResponseDTO.Result r = job.result();
        return new MigrationResultResponseDTO(job.phase(), job.errorCode(),
                r == null ? null : r.status(), r == null ? null : r.reason(),
                r == null ? null : r.fromNode(), r == null ? null : r.toNode(),
                r == null ? null : r.podName(), r == null ? null : r.oldPodCleanup(), job.updatedAt());
    }
}
