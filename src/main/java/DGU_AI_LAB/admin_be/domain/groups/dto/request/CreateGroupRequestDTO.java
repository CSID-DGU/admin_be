package DGU_AI_LAB.admin_be.domain.groups.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Schema(description = "그룹 생성 요청 DTO")
@Builder
public record CreateGroupRequestDTO(

        // 리눅스 그룹 이름으로 /etc/group에 들어간다(상한 32자). 그룹은 쿠버네티스 자원 이름에 쓰이지 않아 밑줄을 허용한다.
        @Schema(description = "생성할 그룹명", example = "developers")
        @NotBlank(message = "그룹명은 필수입니다.")
        @Size(max = 32, message = "그룹명은 32자 이하여야 합니다.")
        @Pattern(regexp = "^[a-z_][a-z0-9_-]*$", message = "그룹명은 소문자나 밑줄로 시작하고 소문자·숫자·밑줄·하이픈만 쓸 수 있습니다.")
        String groupName,

        // 이미 있는 계정을 넣는 값이다. 새 계정 규칙(@UbuntuUsername)보다 먼저 만들어진 계정이 있어 리눅스 규칙으로만 검사한다.
        @Schema(description = "그룹에 추가할 우분투 사용자 이름", example = "user1", required = false)
        @Size(max = 32, message = "사용자 이름은 32자 이하여야 합니다.")
        @Pattern(regexp = "^[a-z_][a-z0-9_-]*$", message = "사용자 이름 형식이 올바르지 않습니다.")
        String ubuntuUsername
) {
}
