package DGU_AI_LAB.admin_be.domain.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 관리자용 노드별 GPU 사용 현황. 사용자 응답(MyGpuUsageResponseDTO)과 달리 프로세스마다 소유자를 담는다 — 관리자 전용 경로에서만 쓴다.
 */
@Builder
@Schema(description = "관리자용 노드 하나의 GPU 사용 현황")
public record AdminGpuUsageResponseDTO(
        @Schema(description = "노드 이름", example = "farm6") String nodeName,
        @Schema(description = "프로세스 정보를 읽을 수 있는지. false면 processes는 비어 있고 '없음'이 아니라 '모름'이다") boolean processesAvailable,
        @Schema(description = "GPU별 현황") List<Gpu> gpus) {

    @Builder
    public record Gpu(
            @Schema(description = "GPU 번호", example = "0") int index,
            @Schema(description = "GPU UUID") String uuid,
            @Schema(description = "GPU 모델명") String model,
            @Schema(description = "GPU 전체 사용률(%)") double utilizationPercent,
            @Schema(description = "GPU 전체 사용 메모리(MiB)") double memoryUsedMiB,
            @Schema(description = "GPU 전체 메모리(MiB)") double memoryTotalMiB,
            @Schema(description = "이 GPU에서 도는 프로세스(메모리 큰 순)") List<Process> processes) {
    }

    @Builder
    public record Process(
            @Schema(description = "소유자 이름. 소유자를 못 찾았으면 null(미확인)", nullable = true) String ownerName,
            @Schema(description = "소유자 우분투 계정. 미확인이면 null", nullable = true) String ubuntuUsername,
            @Schema(description = "소유자 사용자 번호. 미확인이거나 옛 Docker 컨테이너면 null", nullable = true) Long userId,
            @Schema(description = "소유 신청 번호. 미확인이거나 옛 Docker 컨테이너면 null", nullable = true) Long requestId,
            @Schema(description = "컨테이너 ID 앞 12자") String containerId,
            @Schema(description = "이 컨테이너(Pod) 전체 RAM(MiB, working set). 이 프로세스만의 값이 아니라 같은 컨테이너의 행마다 같다. 모르면 null", nullable = true) Double containerRamMiB,
            @Schema(description = "프로세스 이름(파일 이름만)") String processName,
            @Schema(description = "GPU 메모리(MiB)") double memoryUsedMiB,
            @Schema(description = "실행된 지 지난 시간(초)") long elapsedSeconds) {
    }
}
