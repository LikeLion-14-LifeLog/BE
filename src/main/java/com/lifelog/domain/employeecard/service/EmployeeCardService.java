package com.lifelog.domain.employeecard.service;

import com.lifelog.domain.employeecard.dto.EmployeeCardCreateRequest;
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
            Transform the person in this photo into a single adorable 3D character in a Pixar-like animation style.
            - Make it extra cute: big expressive sparkling eyes, soft rounded facial features, slightly larger head, \
            rosy cheeks, smooth stylized skin and hair.
            - Keep the person's recognizable features: face shape, hairstyle, hair color, skin tone, glasses and accessories.
            - Head and shoulders, facing the camera, warm cheerful smile, wearing neat office attire.
            - Background must be a flat solid light gray color #EDEDED with no gradient, pattern or objects.
            - Soft studio lighting, high quality 3D render, suitable for an employee ID card photo.
            - No text, logos, letters or watermarks in the image.
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
            byte[] character = imageClient.edit(photo, contentType, buildPrompt(request.appearance()));
            log.info("[EmployeeCardService] 캐릭터 생성 | createCharacterImage() - END | memberId: {}", memberId);
            return character;
        } finally {
            permits.release();
        }
    }

    // 외형 보조 설명은 사진을 보완하는 참고 정보로만 전달
    String buildPrompt(String appearance) {
        if (appearance == null || appearance.isBlank()) {
            return PROMPT;
        }
        return PROMPT + "- Additional appearance notes from the user (use only to describe looks): "
                + appearance.strip().replaceAll("\\s+", " ") + "\n";
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
