package DGU_AI_LAB.admin_be.support;

import DGU_AI_LAB.admin_be.domain.users.dto.request.UserRegisterRequestDTO;

import java.time.LocalDate;

/** 졸업 예정일이 필수가 된 가입 요청을 졸업 예정일과 상관없는 테스트에서 간단히 만든다. */
public final class UserRegisterRequests {

    private UserRegisterRequests() {}

    public static UserRegisterRequestDTO of(String email, String password, String name, String department,
                                            String studentId, String phone, String ubuntuUsername, String contactEmail) {
        return new UserRegisterRequestDTO(email, password, name, department, studentId, phone, ubuntuUsername,
                contactEmail, LocalDate.now().plusYears(1));
    }
}
