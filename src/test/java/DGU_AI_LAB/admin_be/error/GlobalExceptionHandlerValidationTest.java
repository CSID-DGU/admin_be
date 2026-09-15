package DGU_AI_LAB.admin_be.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검증 실패 응답이 400이면서, 화면이 보여줄 수 있게 어떤 항목이 왜 틀렸는지 메시지를 담는지 확인한다.
 */
@DisplayName("GlobalExceptionHandler — 검증 실패 응답")
class GlobalExceptionHandlerValidationTest {

    record Body(@NotBlank(message = "이름은 필수입니다.") @Size(max = 5, message = "이름은 5자 이하여야 합니다.") String name) {}

    @RestController
    static class TestController {
        @PostMapping("/test/body")
        String body(@RequestBody @Valid Body body) {
            return "ok";
        }

        @GetMapping("/test/{id}")
        String path(@PathVariable @Positive(message = "ID는 양수여야 합니다.") Long id) {
            return "ok";
        }
    }

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("@Validated 프록시나 엔티티 검증에서 올라온 ConstraintViolationException도 400과 위반 메시지로 바꾼다")
    void constraintViolationException_returns400() {
        var violations = validator.getValidator().validate(new Body("toolongname"));

        var response = new GlobalExceptionHandler()
                .handleConstraintViolationException(new jakarta.validation.ConstraintViolationException(violations));

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.getBody().getMessage()).isEqualTo("이름은 5자 이하여야 합니다.");
    }

    @Test
    @DisplayName("본문 검증 실패는 400과 위반 메시지를 돌려준다")
    void bodyViolation_returnsMessage() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"toolongname\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("이름은 5자 이하여야 합니다."));
    }

    @Test
    @DisplayName("경로 변수 검증 실패도 500이 아니라 400과 위반 메시지를 돌려준다")
    void pathVariableViolation_returns400() throws Exception {
        mockMvc.perform(get("/test/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ID는 양수여야 합니다."));
    }
}
