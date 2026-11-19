package com.lifelog.domain.employeecard.service;

import com.lifelog.domain.employeecard.dto.EmployeeCardCreateRequest;
import com.lifelog.domain.employeecard.enums.EmployeeCardGender;
import com.lifelog.domain.employeecard.exception.EmployeeCardErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenAiImageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * 얼굴 사진(+외형 보조 설명)으로 3D 캐릭터 이미지를 만들어 사원증 데이터와 함께 돌려준다.
 * 사진과 결과 이미지는 저장하지 않는다.
 */
@Slf4j
@Service
public class EmployeeCardService {

    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final Set<MediaType> ALLOWED_TYPES = Set.of(
            MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.parseMediaType("image/webp"));

    private static final String PROMPT = """
            이 사진 속 인물을 아주 귀여운 3D 캐릭터 한 명으로 바꿔줘.
            - 더 귀엽게: 크고 반짝이는 눈, 부드럽고 둥근 얼굴선, 살짝 큰 머리, 발그레한 볼, 매끈하게 다듬은 피부와 머리카락.
            - 본인임을 알아볼 수 있는 특징은 유지: 얼굴형, 헤어스타일, 머리색, 피부톤, 안경과 액세서리.
            - 정면을 보는 상반신 구도, 밝고 따뜻한 미소, 단정한 사무용 복장.
            - 배경은 반드시 #EDEDED 밝은 회색 단색. 그라데이션, 무늬, 소품 없음.
            - 부드러운 스튜디오 조명, 고품질 3D 렌더링, 사원증 사진에 어울리게.
            - 이미지 안에 글자, 로고, 문자, 워터마크를 넣지 말 것.
            """;

    private final OpenAiImageClient imageClient;
    // 서버 메모리와 OpenAI 비용을 고려해 동시에 생성하는 수를 제한
    private final Semaphore permits;

    public EmployeeCardService(OpenAiImageClient imageClient,
                               @Value("${employee-card.max-concurrent:2}") int maxConcurrent) {
        this.imageClient = imageClient;
        this.permits = new Semaphore(Math.max(1, maxConcurrent));
    }

    /** 캐릭터 이미지(PNG) 바이트만 만든다. */
    public byte[] createCharacterImage(Long memberId, EmployeeCardCreateRequest request) {
        log.info("[EmployeeCardService] 캐릭터 생성 | createCharacterImage() - START | memberId: {}", memberId);
        MediaType contentType = validatePhoto(request.photo());
        byte[] photo = readBytes(request.photo());

        if (!permits.tryAcquire()) {
            throw new CustomException(EmployeeCardErrorCode.GENERATION_BUSY);
        }
        try {
            byte[] character = imageClient.edit(photo, contentType, buildPrompt(request.gender()));
            log.info("[EmployeeCardService] 캐릭터 생성 | createCharacterImage() - END | memberId: {}", memberId);
            return character;
        } finally {
            permits.release();
        }
    }

    // 선택한 성별을 캐릭터에 반영
    String buildPrompt(EmployeeCardGender gender) {
        return PROMPT + "- 캐릭터의 성별: " + gender.getLabel() + "\n";
    }

    private MediaType validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty() || photo.getSize() > MAX_PHOTO_BYTES || photo.getContentType() == null) {
            throw new CustomException(EmployeeCardErrorCode.INVALID_PHOTO);
        }
        MediaType type;
        try {
            type = MediaType.parseMediaType(photo.getContentType());
        } catch (RuntimeException e) {
            throw new CustomException(EmployeeCardErrorCode.INVALID_PHOTO);
        }
        MediaType normalized = new MediaType(type.getType(), type.getSubtype());
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new CustomException(EmployeeCardErrorCode.INVALID_PHOTO);
        }
        return normalized;
    }

    private byte[] readBytes(MultipartFile photo) {
        try {
            return photo.getBytes();
        } catch (IOException e) {
            throw new CustomException(EmployeeCardErrorCode.INVALID_PHOTO);
        }
    }
}
