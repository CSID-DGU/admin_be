package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.alarm.SlackText;
import DGU_AI_LAB.admin_be.domain.groups.entity.Group;
import DGU_AI_LAB.admin_be.domain.groups.repository.GroupRepository;
import DGU_AI_LAB.admin_be.domain.portRequests.dto.PortChangeValue;
import DGU_AI_LAB.admin_be.domain.requests.dto.request.PortRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.ChangeRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 신청(컨테이너) 단위 변경 요청이 무엇을 어떻게 바꾸는지 접수 알림에 적을 한 줄로 풀어 쓴다.
 * 저장된 값(change_request.new_value)은 종류마다 모양이 다른 JSON이라, 승인자가 읽을 글로 바꾸는 일을 여기 모은다.
 * 사용자가 적은 값(그룹 이름, 포트 사용 목적)은 Slack 글로 다듬어 넣는다.
 */
@Component
@RequiredArgsConstructor
public class ChangeRequestDescriber {

    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    /** 변경 요청을 저장한 트랜잭션 안에서 부른다 — 대상 신청과 신청자의 지금 값을 이전 값으로 읽는다. */
    public String describe(ChangeRequest changeRequest) {
        try {
            return switch (changeRequest.getChangeType()) {
                case EXPIRES_AT -> describeExpiry(changeRequest);
                case GROUP -> describeGroups(changeRequest);
                case PORT -> describePorts(changeRequest);
                default -> changeRequest.getChangeType().label();
            };
        } catch (JsonProcessingException e) {
            // 값은 받을 때 이미 검사했다. 읽지 못해도 접수 알림은 종류 이름만으로 보낸다.
            return changeRequest.getChangeType().label();
        }
    }

    private String describeExpiry(ChangeRequest changeRequest) throws JsonProcessingException {
        LocalDate current = changeRequest.getRequest().getExpiresAt().toLocalDate();
        LocalDate requested = LocalDateTime.parse(objectMapper.readValue(changeRequest.getNewValue(), String.class)).toLocalDate();
        return current + " → " + requested + " (" + ChronoUnit.DAYS.between(current, requested) + "일 연장)";
    }

    private String describeGroups(ChangeRequest changeRequest) throws JsonProcessingException {
        Set<Long> requested = objectMapper.readValue(changeRequest.getNewValue(),
                objectMapper.getTypeFactory().constructCollectionType(Set.class, Long.class));
        List<Group> current = changeRequest.getRequestedBy().getUserGroups().stream()
                .map(userGroup -> userGroup.getGroup())
                .toList();
        Set<Long> currentGids = current.stream().map(Group::getUbuntuGid).collect(Collectors.toSet());
        Set<Long> addedGids = requested.stream().filter(gid -> !currentGids.contains(gid)).collect(Collectors.toSet());
        return "추가 " + groupNames(groupRepository.findAllByUbuntuGidIn(addedGids)) + " (지금 " + groupNames(current) + ")";
    }

    private String describePorts(ChangeRequest changeRequest) throws JsonProcessingException {
        return ports(changeRequest.getOldValue()) + " → " + ports(changeRequest.getNewValue());
    }

    private static String groupNames(List<Group> groups) {
        return groups.isEmpty() ? "없음" : SlackText.line(groups.stream()
                .map(Group::getGroupName)
                .sorted()
                .collect(Collectors.joining(", ")));
    }

    private String ports(String json) throws JsonProcessingException {
        List<PortRequestDTO> ports = json == null ? List.of() : PortChangeValue.parse(json, objectMapper);
        return ports.isEmpty() ? "없음" : ports.stream()
                .map(port -> port.internalPort() + "번 (" + SlackText.line(port.usagePurpose()) + ")")
                .collect(Collectors.joining(", "));
    }
}
