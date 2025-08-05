package DGU_AI_LAB.admin_be.domain.requests.service;

import DGU_AI_LAB.admin_be.domain.requests.dto.request.SaveRequestDTO;
import DGU_AI_LAB.admin_be.domain.requests.dto.response.RequestResponseDTO;
import DGU_AI_LAB.admin_be.domain.requests.entity.Request;
import DGU_AI_LAB.admin_be.domain.requests.repository.RequestRepository;
import DGU_AI_LAB.admin_be.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import DGU_AI_LAB.admin_be.error.ErrorCode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RequestService {
    private final RequestRepository requestRepository;

    // TODO: saveRequestDTO 메서드가 두개인데, 수정이 필요한 것 같아요!
    @Transactional
    public RequestResponseDTO saveRequest(SaveRequestDTO requestDto) {
        if (requestDto.answers() == null || requestDto.answers().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_REQUEST_PARAMETER);
        }

        Request saved = requestRepository.save(requestDto.toEntity());
        return RequestResponseDTO.fromEntity(saved);
    }

    public List<RequestResponseDTO> getAllRequests() {
        return requestRepository.findAll().stream()
                .map(RequestResponseDTO::fromEntity)
                .toList();
    }

    public RequestResponseDTO getRequestById(Long id) {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return RequestResponseDTO.fromEntity(request);
    }
}