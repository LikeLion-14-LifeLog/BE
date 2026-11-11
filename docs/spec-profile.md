# 회원 스펙 임시 저장

`PUT /api/spec-profiles/current/temporary`

Authorization: Bearer JWT, Content-Type: application/json.

회원 ID는 JWT에서 추출한다. CURRENT만 수정하며 희망 직무는 회원에 연결한다.
어학 마스터 데이터가 없으면 HTTP 500과 `어학 데이터 초기화가 필요합니다` 메시지를 반환한다.
저장 API는 초기화 API를 호출하지 않는다.

## 요청

- birthYear, residenceRegion, jobId 필수.
- academicAbilities는 null·생략·[] 모두 허용하며 기존 학력을 삭제한다.
- certificates, languageExamScores, experiences는 필수 배열이며 없으면 [].
- 학력 academicAbilityId, 보유 자격증 specProfileCertificateId,
  어학 성적 languageExamScoreId, 경험 experienceId는 선택 양수 PK.
- PK가 있으면 본인 CURRENT의 행을 요청값으로 덮어쓰고, 없으면 생성한다.
- 요청 PK에서 빠진 기존 행은 삭제한다. 중복 PK와 타인·존재하지 않는 PK는 거절한다.
- 참조 certificateId와 languageExamId는 각 목록에서 중복할 수 없다.
- 모든 연월은 YYYY-MM이며 DB에는 해당 월 1일로 저장한다.
- 평점은 0~4.5이고 평점·만점은 함께 입력하거나 함께 생략한다.
- NUMERIC은 숫자만, GRADE는 등급만, NUMERIC_AND_GRADE는 둘 다 필수.
  NONE은 숫자·등급 모두 null. 시험별 점수 범위·허용 등급은 검사하지 않는다.
- 수상 시 organizerName과 awardGrade 필수, 미수상 시 둘 다 null.

```json
{
  "birthYear": 1999,
  "residenceRegion": "DAEGU",
  "jobId": 1,
  "academicAbilities": null,
  "certificates": [],
  "languageExamScores": [],
  "experiences": []
}
```

## 응답

HTTP 201

```json
{"success":true,"code":"COMMON_201","message":"회원 스펙 임시 저장 성공","data":null}
```

400: 입력값·중복 항목·점수유형 위반. 401: JWT 인증 실패.
404: CURRENT·참조 정보·본인 하위 항목 없음.
500: 어학 미초기화 또는 CURRENT 중복.

## 트랜잭션 및 배포

동일 CURRENT 저장은 프로필 행 잠금으로 직렬화한다. 실패하면 삭제·수정·생성 전체를 롤백한다.
프로필 생성은 회원 행 잠금과 CURRENT 1개·TARGET 최대 3개 정책을 사용한다.
temporaryStored 필드는 사용하지 않는다.

Flyway _3에서 스키마를 확장하고 _4에서 CURRENT가 없는 회원을 백필한다.
중복 희망 직무·어학 시험 행 또는 사용 중인 과거 TARGET 전용 컬럼이 있으면 이관을 중단한다.
기존 데이터를 임의로 삭제하지 않는다. DB 기본값은 type=CURRENT, sort_order=0이다.

