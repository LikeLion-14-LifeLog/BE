# 🪪 라이프로그 (LifeLog) — Backend
> LIKELION 14th · Line 4 Hackathon

**막연했던 커리어, 숫자로 선명하게.**

"공공 고용 데이터로 보는 내 연봉과 다음 커리어."

학력·전공·경험 등 내 스펙을 입력하면 공공 고용 패널 데이터를 기반으로 **예상 연봉 진단 · 조건별 연봉 시뮬레이션 · 선배들의 커리어 경로 · 맞춤 채용 공고**까지 제공하는 커리어 진단 서비스의 백엔드입니다.

Spring Boot + PostgreSQL 기반의 REST API 서버로, 회원 인증, 스펙 프로필 관리, AI 연봉 분석, 커리어 경로 통계, 채용 공고 수집·추천을 담당합니다.

<br/>

## 🔹 주요 기능

### 1. 🔐 인증
- 이메일 회원가입 · 로그인
- JWT Access Token 발급 및 `JwtAuthenticationFilter`로 요청마다 인증 처리
- Spring Security 기반 엔드포인트 접근 제어와 CORS 설정

### 2. 📝 스펙 프로필
- 기본 정보 · 학력 · 자격증 · 어학 점수 · 경험 저장 및 임시 저장
- 전공 · 자격증 · 어학시험 · 직무(KECO 2025) · 거주 지역 기준 데이터 제공
- 기준 데이터는 CSV로 초기 적재 (`/api/admin/**/init`)

### 3. 💰 연봉 진단 & 시뮬레이션
- 스펙 기반 AI 예상 연봉 분석 — 실행 상태를 저장하고 진행 상황을 **SSE**(`text/event-stream`)로 실시간 전송
- 분석 요청은 큐와 워커로 순차 처리하고, 실행 이력 · 이벤트 로그 · 취소를 관리
- 학력 · 경험 · 기업 규모 등 조건을 바꾼 연봉 시뮬레이션
- 목표 스펙 저장과 AI 대안 추천 · 평가 (CURRENT 잠금으로 동시 저장 직렬화)

### 4. 🧭 커리어 경로
- 동일 전공 · 조건 선배들의 취업 · 이직 경로 그래프 (공공 패널 데이터 기반)
- 연도별 관측치, 연봉 분석, 인사이트, 개인화 경로 추천
- 탐색형 통계 API

### 5. 💼 채용 공고 & 대시보드
- 서울 열린데이터광장 채용정보를 **스케줄러로 주기 동기화** (수동 동기화 · 상태 조회 API 제공)
- 직무 분류 · 필터 · 페이지네이션 조회, 상세 조회, 북마크
- 대시보드에서 최근 진단 요약 · 추천 공고 · 스펙 입력 현황 제공

### 6. ⚠️ 공통
- `CustomException` + 도메인별 ErrorCode로 일관된 에러 응답
- Swagger(springdoc) API 문서
- Flyway로 DB 스키마 버전 관리

<br/>

## 🚀 기술 스택
- **Language:** Java 21
- **Framework:** Spring Boot 4.1, Spring Web MVC
- **보안 / 인증:** Spring Security, JWT (jjwt 0.12)
- **Database:** PostgreSQL 17, Spring Data JPA (Hibernate 7)
- **Migration:** Flyway
- **AI:** OpenAI API
- **외부 데이터:** 서울 열린데이터광장 Open API, 공공 고용 패널 CSV
- **API 문서:** springdoc-openapi (Swagger UI)
- **Test:** JUnit 5, Spring Boot Test, H2, PostgreSQL 통합 테스트
- **Infra:** Docker, Docker Compose, AWS EC2
- **CI / CD:** GitHub Actions, GHCR

<br/>

## 🏗️ 아키텍처 개요
```
🖥️ Frontend (Vercel)
    │  Authorization: Bearer {JWT}
    ▼
🌐 Spring Boot API (AWS EC2 · Docker)
    ├─ Spring Security + JWT Filter   (인증 · CORS)
    ├─ Controller / Service / Repository
    ├─ OpenApiRequestWorker            (연봉 분석 요청 큐 처리)
    ├─ SSE Registry                    (분석 진행 상황 실시간 전송)
    └─ JobPostingSyncScheduler         (채용 공고 주기 동기화)
          │
          ├─ 🐘 PostgreSQL 17           (Flyway 마이그레이션)
          ├─ 🤖 OpenAI API              (연봉 분석 · 목표 추천)
          └─ 🏛️ 서울 열린데이터광장       (채용정보 recMntList)

API
    ├─ /api/auth                    회원가입 · 로그인
    ├─ /api/spec-profile            스펙 프로필 · 연봉 분석(SSE) · 목표 스펙
    ├─ /api/v1/career-paths         커리어 경로 · 연봉 분석 · 개인화 추천
    ├─ /api/v1/statistics           탐색형 통계
    ├─ /api/job-postings            채용 공고 · 북마크
    ├─ /api/dashboard               대시보드
    └─ /api/{majors, certifications, language-exams, jobs, residential-areas}   기준 데이터
```

### 🔄 CI / CD
```
PR → develop / main   ──▶  CI : Gradle build + test (PostgreSQL service container)
merge → develop       ──▶  CD : Docker image build ─▶ GHCR push ─▶ EC2 docker compose pull & up
```

<br/>

## 📁 프로젝트 구조
```
BE/
├─ .github/workflows/          # CI · CD
├─ scripts/                    # EC2 초기 설정 스크립트
├─ compose.yaml                # 로컬 PostgreSQL
├─ compose.prod.yaml           # 운영 배포 (app + DB)
├─ Dockerfile
└─ src/
   ├─ main/
   │  ├─ java/com/lifelog/
   │  │  ├─ domain/
   │  │  │  ├─ auth/              # 회원가입 · 로그인 · JWT
   │  │  │  ├─ member/            # 회원
   │  │  │  ├─ specprofile/       # 스펙 프로필 · 연봉 분석 · 시뮬레이션 · 목표 스펙
   │  │  │  ├─ careerpath/        # 커리어 경로 · 연봉 분석 · 개인화 추천
   │  │  │  ├─ statistics/        # 탐색형 통계
   │  │  │  ├─ jobposting/        # 채용 공고 동기화 · 조회 · 북마크
   │  │  │  ├─ dashboard/         # 대시보드
   │  │  │  ├─ company/           # 기업
   │  │  │  └─ job · major · certification · languageexam · residentialarea   # 기준 데이터
   │  │  └─ global/
   │  │     ├─ config/            # Security · CORS 등 설정
   │  │     ├─ security/          # JWT 인증 필터
   │  │     └─ infra/             # OpenAI · 서울 Open API 클라이언트
   │  └─ resources/
   │     ├─ db/migration/         # Flyway SQL
   │     ├─ prompts/              # AI 프롬프트
   │     └─ application.yaml
   └─ test/                       # 단위 · 통합 테스트
```
각 도메인은 `controller` · `service` · `repository` · `entity` · `dto` · `exception` 패키지로 나눕니다.
Swagger 명세는 `*ControllerDocs` 인터페이스로 분리해 컨트롤러 코드를 깔끔하게 유지합니다.

<br/>

## ⚙️ 로컬 실행
```bash
# 1. 환경 변수 설정 (.env.example 참고)
cp .env.example .env

# 2. PostgreSQL 실행
docker compose up -d

# 3. 서버 실행 (http://localhost:8080)
./gradlew bootRun

# 4. 테스트
./gradlew test
```
| 변수 | 설명 |
|---|---|
| `DB_PASSWORD` | PostgreSQL 비밀번호 |
| `DB_PORT` | 로컬 DB 포트 (기본 `5433`) |
| `JWT_SECRET` | JWT 서명 키 (Base64) |
| `SEOUL_OPENAPI_KEY` | 서울 열린데이터광장 API 키 |
| `OPEN_API_KEY` | OpenAI API 키 |
| `OPEN_API_MODEL` | 사용할 OpenAI 모델 |

API 문서: `http://localhost:8080/swagger-ui/index.html`

<br/>

## 🤝 협업 규칙

### 커밋 컨벤션
`:gitmoji: Type: 작업 내용` 형식으로 작성합니다. (예: `:sparkles: Feat: 채용 공고 북마크 API 추가`)

- 🎉 **Start:** Start New Project [:tada:]
- ✨ **Feat:** 새로운 기능을 추가 [:sparkles:]
- 🐛 **Fix:** 버그 수정 [:bug:]
- 🎨 **Design:** CSS 등 사용자 UI 디자인 변경 [:art:]
- ♻️ **Refactor:** 코드 리팩토링 [:recycle:]
- 🔧 **Settings:** Changing configuration files [:wrench:]
- 🗃️ **Comment:** 필요한 주석 추가 및 변경 [:card_file_box:]
- ➕ **Dependency/Plugin:** Add a dependency/plugin [:heavy_plus_sign:]
- 📝 **Docs:** 문서 수정 [:memo:]
- 🔀 **Merge:** Merge branches [:twisted_rightwards_arrows:]
- 🚀 **Deploy:** Deploying stuff [:rocket:]
- 🚚 **Rename:** 파일 혹은 폴더명을 수정하거나 옮기는 작업만인 경우 [:truck:]
- 🔥 **Remove:** 파일을 삭제하는 작업만 수행한 경우 [:fire:]
- ⏪️ **Revert:** 전 버전으로 롤백 [:rewind:]

### 브랜치 · PR
- 기능 브랜치에서 작업한 뒤 `develop`으로 PR을 올립니다.
- PR은 CI(빌드 · 테스트) 통과를 확인한 뒤 머지합니다.
- `develop`에 머지되면 CD로 EC2에 자동 배포됩니다.

<br/>

## 👥 팀 구성 (Backend)
| <a href="https://github.com/Ain134"><img src="https://github.com/Ain134.png" width="100"/></a> | <a href="https://github.com/nahjjun"><img src="https://github.com/nahjjun.png" width="100"/></a> | <a href="https://github.com/ju0321"><img src="https://github.com/ju0321.png" width="100"/></a> |
|:---:|:---:|:---:|
| [조아인 (@Ain134)](https://github.com/Ain134) | [나형준 (@nahjjun)](https://github.com/nahjjun) | [오지우 (@ju0321)](https://github.com/ju0321) |
| Backend | Backend | Backend |
