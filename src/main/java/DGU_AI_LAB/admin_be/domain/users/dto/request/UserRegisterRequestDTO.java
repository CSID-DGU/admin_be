package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.domain.users.entity.User;
import DGU_AI_LAB.admin_be.global.validation.UbuntuUsername;
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

        // 화면도 8자 이상을 요구한다. BCrypt는 72바이트 뒤를 버리므로 그 이상은 받지 않는다.
        @Schema(description = "비밀번호", example = "strongPassword123!")
        @NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
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

        // 연락처 변경(PhoneUpdateRequestDTO)과 같은 형식을 요구한다.
        @Schema(description = "전화번호", example = "010-1234-5678")
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "유효한 전화번호 형식이 아닙니다. (예: 010-1234-5678)")
        String phone,

        // 웹 계정 하나당 우분투 계정 하나 — 가입 시 정하면 이후 모든 컨테이너가 이 이름을
        // 쓰므로 홈 디렉터리(/home/<username>)가 그대로 이어진다. 규칙은 @UbuntuUsername 참고.
        @Schema(description = "Ubuntu 계정명 (3~32자, 소문자로 시작, 소문자·숫자·하이픈)", example = "hongildong")
        @UbuntuUsername
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
