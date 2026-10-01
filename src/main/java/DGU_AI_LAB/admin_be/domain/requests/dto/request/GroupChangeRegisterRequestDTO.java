package DGU_AI_LAB.admin_be.domain.requests.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 공용 그룹 작업 등록 본문. op 에 따라 쓰는 값이 다르다.
 *
 * @param requestId 그룹 작업 번호. 결과도 이 번호로 조회한다
 * @param op        create / add / remove
 * @param username  add·remove: 그룹이 바뀌는 리눅스 계정, create: 만드는 사람의 계정(없으면 생략)
 * @param name      create: 만들 그룹명, remove: 뺄 그룹명
 * @param members   create: 만들면서 넣을 계정
 * @param groups    add: 더할 그룹명
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupChangeRegisterRequestDTO(
        @JsonProperty("request_id") Long requestId,
        String op,
        String username,
        String name,
        List<String> members,
        List<String> groups
) {
    /** @param requesterUsername 만드는 사람의 리눅스 계정(없으면 null). 작업 기록이 누구의 요청인지 남기는 데 쓴다 */
    public static GroupChangeRegisterRequestDTO create(Long operationId, String requesterUsername, String groupName,
                                                       List<String> members) {
        return new GroupChangeRegisterRequestDTO(operationId, "create", requesterUsername, groupName, members, null);
    }

    public static GroupChangeRegisterRequestDTO add(Long operationId, String username, List<String> groups) {
        return new GroupChangeRegisterRequestDTO(operationId, "add", username, null, null, groups);
    }

    public static GroupChangeRegisterRequestDTO remove(Long operationId, String username, String groupName) {
        return new GroupChangeRegisterRequestDTO(operationId, "remove", username, groupName, null, null);
    }
}
