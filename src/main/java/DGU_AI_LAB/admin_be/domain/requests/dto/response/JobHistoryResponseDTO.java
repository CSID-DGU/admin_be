package DGU_AI_LAB.admin_be.domain.requests.dto.response;

/**
 * 신청 상세 화면용 작업 기록. 한 신청의 생성 작업(승인)과 회수 작업(만료·삭제) 단계 기록을 함께 담는다.
 */
public record JobHistoryResponseDTO(
        JobStepsResponseDTO provision,
        JobStepsResponseDTO revoke,
        JobStepsResponseDTO migrate
) {
    public JobHistoryResponseDTO(JobStepsResponseDTO provision, JobStepsResponseDTO revoke) {
        this(provision, revoke, null);
    }
}
