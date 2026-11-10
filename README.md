# LifeLog-BE
> LIKELION 14th · Line 4 Hackathon

## 🎯 Git Convention
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


## 자격증 기준 데이터 초기화

`PUT /api/admin/certifications/init` — 요청 본문·파일 업로드 없이 배포된 `data/certification.csv`를 읽습니다.
현재 인증은 미적용이며 추후 관리자 인증을 연결합니다.

- UTF-8 CSV의 헤더: `name,code,main_category,type,authorized_institution_name`
- 코드·기관명 빈 값은 NULL로 저장합니다. 코드로 식별하고, 코드가 없으면 `(type, name)`으로 식별합니다.
- 기존 코드 없는 행에 코드가 추가되면 PK를 유지하며 보완합니다. 원본에 코드가 없어도 기존 코드는 보존합니다.
- 코드 없는 행의 이름·유형 변경은 동일성을 판별할 수 없으므로 별도 매핑이 필요합니다.
- 기존 PK·FK 및 원본 밖 행을 보존합니다. 중복 원본·잘못된 ENUM은 전체 실패하며 저장 오류는 전체 롤백합니다.
- 원본의 E 분류 오타 8건을 보정했고 공백을 정리했습니다. 기준 데이터는 1,424건입니다.

마이그레이션 소스 파일 `V20261110_1_modify_certification_field.sql`은 `processResources`에서
`V20261110_1__modify_certification_field.sql`로 패키징됩니다. 수동 배포 시에도 패키징된 파일명을 사용합니다.
기존 NULL/미정의 대분류, 길이 제한 초과, 코드 없는 동일 유형·이름 중복은 배포 전 보정해야 합니다.
PK와 FK는 변경하지 않으며 기존 보조 컬럼은 보존합니다. `issuer` 컬럼명 변경은 구버전 앱과 호환되지 않습니다.

PostgreSQL 마이그레이션 통합 테스트는 별도 테스트 DB의 임시 스키마에서 수행합니다.
`CERTIFICATION_TEST_DB_URL`, `CERTIFICATION_TEST_DB_USER`, `CERTIFICATION_TEST_DB_PASSWORD` 설정 후
`gradlew.bat test --tests '*CertificationMigrationIntegrationTest'`를 실행합니다. 미설정 시 해당 테스트는 건너뜁니다.
