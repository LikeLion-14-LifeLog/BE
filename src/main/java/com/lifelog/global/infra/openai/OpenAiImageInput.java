package com.lifelog.global.infra.openai;

import org.springframework.http.MediaType;

/**
 * OpenAI 이미지 편집에 보내는 입력 이미지 한 장.
 * @param bytes 원본 바이트
 * @param contentType 형식 (png·jpeg·webp)
 * @param filename multipart에 붙일 파일 이름
 */
public record OpenAiImageInput(byte[] bytes, MediaType contentType, String filename) {
}
