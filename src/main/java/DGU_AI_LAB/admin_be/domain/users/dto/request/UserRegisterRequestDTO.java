package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청 DTO")
public record UserRegisterRequestDTO(

        // 길이 상한은 User 엔티티의 컬럼 정의를 따른다.
        // 상한이 없으면 DB 컬럼 길이를 넘는 값이 서비스까지 내려가 500으로 끝난다.
        @Schema(description = "이메일 주소", example = "user@example.com")
        @Email @NotBlank @Size(max = 100)
        String email,

        @Schema(description = "비밀번호", example = "strongPassword123!")
        @NotBlank @Size(max = 255)
        String password,

        @Schema(description = "사용자 이름", example = "이소은")
        @NotBlank @Size(max = 100)
        String name,

        @Schema(description = "학과", example = "컴퓨터공학과")
        @NotBlank @Size(max = 100)
        String department,

        @Schema(description = "학번", example = "202312345")
        @NotBlank @Size(max = 100)
        String studentId,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @NotBlank @Size(max = 100)
        String phone,

        // 웹 계정 하나당 우분투 계정 하나 — 가입 시 정하면 이후 모든 컨테이너가 이 이름을
        // 쓰므로 홈 디렉터리(/home/<username>)가 그대로 이어진다. 리눅스 유저네임 규칙에
        // 맞춰 소문자로 시작하고 소문자/숫자/밑줄/하이픈만 허용한다.
        @Schema(description = "Ubuntu 계정명 (3~50자, 소문자로 시작)", example = "hongildong")
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$", message = "Ubuntu username must start with a lowercase letter and contain only lowercase letters, digits, '_' or '-'")
        String ubuntuUsername
) {
        /** 비밀번호는 서비스에서 암호화한 값을 넘겨서 처리 */
        public User toEntity(String encodedPassword) {
                return User.builder()
                        .email(email)
                        .password(encodedPassword)
                        .name(name)
                        .department(department)
                        .studentId(studentId)
                        .phone(phone)
                        .ubuntuUsername(ubuntuUsername)
                        // role, isActive는 엔티티의 @Builder.Default 로 기본값 사용
                        .build();
        }
}
