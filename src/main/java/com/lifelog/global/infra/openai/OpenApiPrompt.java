package com.lifelog.global.infra.openai;

import tools.jackson.databind.JsonNode;

/** 개발자 지시, 입력 데이터, 응답 계약을 분리한 OpenAI 요청 입력이다. */
public record OpenApiPrompt(String developerInstructions, String inputJson,
                            String schemaName, JsonNode responseSchema) {}
