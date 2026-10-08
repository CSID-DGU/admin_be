package DGU_AI_LAB.admin_be.domain.portRequests.dto;

import DGU_AI_LAB.admin_be.domain.portRequests.entity.PortRequests;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 포트 변경 요청의 값(change_request.old_value·new_value) — 추가 포트 전체 목록의 JSON 이다:
 * {@code [{"internalPort":3000,"usagePurpose":"웹 서버"}]}. 빈 목록은 추가 포트를 모두 뺀다는 뜻이다.
 *
 * <p>컨테이너 이미지에 달린 값(기본 포트, noVNC 포트)은 여기 한 곳에만 둔다. 최종 판정은 작업을 받는
 * config-server 가 하고, 여기서는 승인 전에 걸러 관리자가 승인할 때에야 실패하는 일을 줄인다.
 */
public final class PortChangeValue {

    /** 변경 요청으로 바꿀 수 없는 포트: 모든 컨테이너의 기본 포트(ssh·jupyter)와 컨테이너를 만들 때만 켤 수 있는 noVNC. */
    private static final Set<Integer> PROTECTED_PORTS = Set.of(22, 8888, 6080);
    /** 이름으로 기본 포트를 찾고(ssh·jupyter) noVNC 를 켜므로(novnc·vnc), 추가 포트의 사용 목적으로는 받지 않는다. */
    private static final Set<String> RESERVED_PURPOSES = Set.of("ssh", "jupyter", "novnc", "vnc");
    /** 신청할 때의 한도(SaveRequestRequestDTO.portRequests)와 같다. */
    static final int MAX_PORTS = 10;
    /** config-server 의 포트 배정 기록(nodeport_allocations.purpose) 길이. */
    static final int PURPOSE_MAX_LENGTH = 255;

    private PortChangeValue() {
    }

    public static boolean isProtected(Integer internalPort) {
        return PROTECTED_PORTS.contains(internalPort);
    }

    /** 저장된 값을 읽는다. 형식은 받을 때 이미 검사했다. */
    public static List<PortRequestDTO> parse(String json, ObjectMapper objectMapper) throws JsonProcessingException {
        return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PortRequestDTO.class));
    }

    /** 사용자가 보낸 값을 검사해 읽는다. 받을 수 없는 값이면 INVALID_INPUT_VALUE. */
    public static List<PortRequestDTO> validated(String json, ObjectMapper objectMapper) {
        List<PortRequestDTO> ports;
        try {
            ports = parse(json, objectMapper);
        } catch (JsonProcessingException e) {
            throw invalid("포트 목록의 형식이 올바르지 않습니다.");
        }
        if (ports == null || ports.contains(null)) {
            throw invalid("포트 목록의 형식이 올바르지 않습니다.");
        }
        if (ports.size() > MAX_PORTS) {
            throw invalid("추가 포트는 " + MAX_PORTS + "개 이하여야 합니다.");
        }
        Set<Integer> seen = new HashSet<>();
        for (PortRequestDTO port : ports) {
            Integer number = port.internalPort();
            if (number == null || number < 1 || number > 65535) {
                throw invalid("내부 포트는 1~65535 범위여야 합니다.");
            }
            if (isProtected(number)) {
                throw invalid("기본 포트(22, 8888)와 noVNC 포트(6080)는 바꿀 수 없습니다.");
            }
            if (!seen.add(number)) {
                throw invalid("같은 포트를 두 번 적을 수 없습니다: " + number);
            }
            String purpose = port.usagePurpose();
            if (purpose == null || purpose.isBlank() || purpose.length() > PURPOSE_MAX_LENGTH) {
                throw invalid("포트 사용 목적은 1~" + PURPOSE_MAX_LENGTH + "자여야 합니다.");
            }
            if (RESERVED_PURPOSES.contains(purpose.trim().toLowerCase(Locale.ROOT))) {
                throw invalid("포트 사용 목적으로 쓸 수 없는 이름입니다: " + purpose.trim());
            }
        }
        return ports;
    }

    /** 신청에 지금 달린 추가 포트(바꿀 수 없는 포트 제외)를 값 형식으로 적는다. */
    public static String currentValue(List<PortRequests> current, ObjectMapper objectMapper) throws JsonProcessingException {
        return objectMapper.writeValueAsString(current.stream()
                .filter(port -> !isProtected(port.getInternalPort()))
                .map(port -> new PortRequestDTO(port.getInternalPort(), port.getUsagePurpose()))
                .toList());
    }

    public static Set<Integer> numbers(List<PortRequestDTO> ports) {
        return ports.stream().map(PortRequestDTO::internalPort).collect(Collectors.toSet());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(message, ErrorCode.INVALID_INPUT_VALUE);
    }
}
