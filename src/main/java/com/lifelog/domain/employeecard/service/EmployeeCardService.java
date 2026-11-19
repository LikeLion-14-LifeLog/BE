package com.lifelog.domain.employeecard.service;

import com.lifelog.domain.employeecard.dto.EmployeeCardCreateRequest;
import com.lifelog.domain.employeecard.enums.EmployeeCardGender;
import com.lifelog.domain.employeecard.exception.EmployeeCardErrorCode;
import com.lifelog.global.exception.CustomException;
import com.lifelog.global.infra.openai.OpenAiImageClient;
import com.lifelog.global.infra.openai.OpenAiImageInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * 얼굴 사진과 스타일 참고 이미지로 3D 캐릭터 이미지(PNG)를 만든다.
 * 사진과 결과 이미지는 저장하지 않는다.
 */
@Slf4j
@Service
public class EmployeeCardService {

    static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;
    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");
    private static final Set<MediaType> ALLOWED_TYPES = Set.of(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, IMAGE_WEBP);

    static final String PROMPT_PATH = "prompts/employee-card-character.v1.txt";
    // 사용자 사진 뒤에 파일 이름순으로 붙여 보내는 스타일 참고 이미지
    static final String REFERENCE_PATTERN = "classpath*:prompts/employee-card-references/*";

    private final OpenAiImageClient imageClient;
    private final String basePrompt;
    private final List<OpenAiImageInput> references;
    // 서버 메모리와 OpenAI 비용을 고려해 동시에 생성하는 수를 제한
    private final Semaphore permits;

    public EmployeeCardService(OpenAiImageClient imageClient,
                               @Value("${employee-card.max-concurrent:2}") int maxConcurrent) {
        this.imageClient = imageClient;
        this.permits = new Semaphore(Math.max(1, maxConcurrent));
        this.basePrompt = loadPrompt();
        this.references = loadReferences();
    }

    /** 캐릭터 이미지(PNG) 바이트만 만든다. */
    public byte[] createCharacterImage(Long memberId, EmployeeCardCreateRequest request) {
        log.info("[EmployeeCardService] 캐릭터 생성 | createCharacterImage() - START | memberId: {}", memberId);
        MediaType contentType = validatePhoto(request.photo());
        byte[] photo = readBytes(request.photo());

        // 첫 번째 이미지는 사용자 사진, 그 뒤는 참고 이미지 (프롬프트가 이 순서를 전제로 함)
        List<OpenAiImageInput> images = new ArrayList<>(references.size() + 1);
        images.add(new OpenAiImageInput(photo, contentType, "photo." + contentType.getSubtype()));
        images.addAll(references);

        if (!permits.tryAcquire()) {
            throw new CustomException(EmployeeCardErrorCode.GENERATION_BUSY);
        }
        try {
            byte[] character = imageClient.edit(images, buildPrompt(request.gender()));
            log.info("[EmployeeCardService] 캐릭터 생성 | createCharacterImage() - END | memberId: {}", memberId);
            return character;
        } finally {
            permits.release();
        }
    }

    // 선택한 성별을 캐릭터에 반영
    String buildPrompt(EmployeeCardGender gender) {
        return basePrompt + "\n성별:\n- 캐릭터의 성별: " + gender.getLabel() + "\n";
    }

    private static String loadPrompt() {
        try (InputStream input = new ClassPathResource(PROMPT_PATH).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8).strip() + "\n";
        } catch (IOException e) {
            throw new IllegalStateException("사원증 캐릭터 프롬프트를 읽을 수 없습니다: " + PROMPT_PATH, e);
        }
    }

    private static List<OpenAiImageInput> loadReferences() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(REFERENCE_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("사원증 참고 이미지 폴더를 읽을 수 없습니다", e);
        }
        List<OpenAiImageInput> loaded = Arrays.stream(resources)
                .filter(resource -> resource.getFilename() != null && imageType(resource.getFilename()) != null)
                .sorted(Comparator.comparing(Resource::getFilename))
                .map(EmployeeCardService::toInput)
                .toList();
        if (loaded.isEmpty()) {
            log.warn("[EmployeeCardService] 사원증 참고 이미지가 없어 사용자 사진만 보냅니다 | pattern: {}", REFERENCE_PATTERN);
        } else {
            log.info("[EmployeeCardService] 사원증 참고 이미지 {}장 로드 | {}", loaded.size(),
                    loaded.stream().map(OpenAiImageInput::filename).toList());
        }
        return loaded;
    }

    private static OpenAiImageInput toInput(Resource resource) {
        String filename = resource.getFilename();
        try (InputStream input = resource.getInputStream()) {
            return new OpenAiImageInput(input.readAllBytes(), imageType(filename), filename);
        } catch (IOException e) {
            throw new IllegalStateException("사원증 참고 이미지를 읽을 수 없습니다: " + filename, e);
        }
    }

    // 확장자로 형식 판단 (지원하지 않으면 null)
    private static MediaType imageType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".webp")) {
            return IMAGE_WEBP;
        }
        return null;
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
