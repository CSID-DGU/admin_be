package DGU_AI_LAB.admin_be.domain.monitoring.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

/**
 * 내 서버의 GPU별 사용 현황. 다른 사용자는 사람 수와 메모리 합계로만 담는다 —
 * 이름·계정·Pod·프로세스 등 식별 정보는 이 응답에 넣지 않는다.
 */
@Builder
@Schema(description = "내 서버의 GPU별 사용 현황")
public record MyGpuUsageResponseDTO(
        @Schema(description = "서버(노드) 이름", example = "farm1") String nodeName,
        @Schema(description = "GPU별 현황") List<Gpu> gpus,
        @Schema(description = "프로세스·다른 사용자 정보를 읽었는지. false면 내 프로세스 없음·다른 사용자 0명이 '없음'이 아니라 '모름'이다")
        boolean processesAvailable) {

    @Builder
    public record Gpu(
            @Schema(description = "GPU 번호", example = "0") int index,
            @Schema(description = "GPU 모델명") String modelName,
            @Schema(description = "GPU 전체 사용률(%)") double utilizationPercent,
            @Schema(description = "GPU 전체 사용 메모리(MiB)") double memoryUsedMiB,
            @Schema(description = "GPU 전체 메모리(MiB)") double memoryTotalMiB,
            @Schema(description = "내 프로세스(이름·실행 시간·GPU 메모리)") List<Process> myProcesses,
            @Schema(description = "같은 GPU를 쓰는 다른 사용자 수") int otherUserCount,
            @Schema(description = "다른 사용자들의 GPU 메모리 합계(MiB)") double otherMemoryMiB) {
    }

    @Builder
    public record Process(
            @Schema(description = "프로세스 이름") String name,
            @Schema(description = "실행된 지 지난 시간(초)") long elapsedSeconds,
            @Schema(description = "GPU 메모리(MiB)") double memoryMiB) {
    }
}
