package DGU_AI_LAB.admin_be.domain.users.dto.request;

import DGU_AI_LAB.admin_be.domain.users.entity.Role;
import DGU_AI_LAB.admin_be.domain.users.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원가입 요청 DTO")
public record UserRegisterRequestDTO(

        @Schema(description = "이메일 주소", example = "user@example.com")
        @Email @NotBlank
        String email,

        @Schema(description = "비밀번호", example = "strongPassword123!")
        @NotBlank
        String password,

        @Schema(description = "사용자 이름", example = "이소은")
        @NotBlank
        String name,

        @Schema(description = "학과", example = "컴퓨터공학과")
        @NotBlank
        String department,

        @Schema(description = "학번", example = "202312345")
        @NotBlank
        String studentId,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @NotBlank
        String phone
) {
        public User toEntity(String encodedPassword) {
                return User.builder()
                        .email(email)
                        .password(encodedPassword)
                        .name(name)
                        .department(department)
                        .studentId(studentId)
                        .phone(phone)
                        .role(Role.USER)
                        .isActive(true)
                        .build();
        }
}

