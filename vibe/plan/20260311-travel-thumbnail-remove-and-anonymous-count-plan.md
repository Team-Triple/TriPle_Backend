# PLAN: Travel API ThumbnailUrl 제거 및 비로그인 목록 count 응답

## 0. Meta

- 작성일: 2026-03-11
- 프로젝트: spring-clone
- 작성자: AI Agent
- 관련 이슈/요청: 모든 여행 API의 `thumbnailUrl` 제거(TravelItinerary 엔티티 포함), 비로그인 여행 목록 조회 시 `TravelItineraryCursorResponseDto`에 `count` 추가 및 숫자 중심 응답

## 1. JOB 분석

- JOB 목적: 여행 API 계약과 `TravelItinerary` 엔티티에서 `thumbnailUrl`을 제거하고, 비로그인 사용자의 여행 목록 조회를 401 대신 `count` 기반 응답으로 전환한다.
- 기대 결과: 여행 생성/수정/조회 스펙과 `TravelItinerary` 모델에서 `thumbnailUrl`이 사라지고, 비로그인 목록 조회 시 상세 `items` 대신 숫자(`count`)를 사용할 수 있다.
- 범위(In Scope): `travel` 도메인 DTO/컨트롤러/서비스/리포지토리/엔티티, 엔티티 시그니처 변경에 따른 연관 모듈(group recent travel, invoice/payment 테스트 fixture), travel/group 관련 문서
- 제외 범위(Out of Scope): DB 컬럼 물리 삭제/마이그레이션, group 엔티티(`thumbNailUrl`) 정책 변경

## 2. 현재 상태 진단

- 현재 코드 구조: `TravelItinerarySaveRequestDto`, `TravelItineraryUpdateRequestDto`, `TravelItineraryCursorResponseDto.TravelSummaryDto`, `TravelItinerary` 엔티티가 `thumbnailUrl`을 포함한다.
- 재사용 가능 컴포넌트: `@LoginUser` nullable 해석(`SessionManager.getUserId`)과 기존 커서 페이징 로직(`browseTravels`)을 활용할 수 있다.
- 제약사항: 목록 조회는 현재 `@LoginRequired`로 비로그인 접근 시 401 고정이며, 테스트/REST Docs가 동일 계약을 전제로 작성되어 있다.

## 3. TASK 분해

| TASK ID | 작업명 | 목적 | 대상 파일(예상) | CS 학습 포인트 | 완료 조건(DoD) |
|---|---|---|---|---|---|
| T1 | Travel API/엔티티에서 thumbnailUrl 완전 제거 | 생성/수정/목록 API와 엔티티 모델에서 thumbnailUrl 제거 | `src/main/java/org/triple/backend/travel/dto/request/TravelItinerarySaveRequestDto.java`, `src/main/java/org/triple/backend/travel/dto/request/TravelItineraryUpdateRequestDto.java`, `src/main/java/org/triple/backend/travel/dto/response/TravelItineraryCursorResponseDto.java`, `src/main/java/org/triple/backend/travel/entity/TravelItinerary.java`, `src/main/java/org/triple/backend/group/service/GroupService.java`, `src/main/java/org/triple/backend/group/dto/response/GroupDetailResponseDto.java` | 도메인 모델 필드 제거 시 하위 시그니처/매핑 파급 관리 | Travel API DTO/엔티티에서 `thumbnailUrl`이 제거되고, 연관 컴파일 오류 없이 빌드된다. |
| T2 | 비로그인 여행 목록 조회 count 응답 도입 | 비로그인 목록 조회를 숫자 기반으로 제공 | `src/main/java/org/triple/backend/travel/controller/TravelItineraryController.java`, `src/main/java/org/triple/backend/travel/service/TravelItineraryService.java`, `src/main/java/org/triple/backend/travel/repository/TravelItineraryJpaRepository.java`, `src/main/java/org/triple/backend/travel/dto/response/TravelItineraryCursorResponseDto.java` | 인증 상태별 조회 정책 분기(권한/데이터 최소화) | 비로그인 요청이 401이 아닌 `count` 응답을 반환하고, 로그인 멤버는 기존 커서 목록을 유지한다. |
| T3 | 테스트/문서 스펙 동기화 | 변경된 API/엔티티 계약을 테스트와 문서에 반영 | `src/test/java/org/triple/backend/travel/unit/controller/TravelControllerTest.java`, `src/test/java/org/triple/backend/travel/unit/service/TravelItineraryServiceTest.java`, `src/test/java/org/triple/backend/travel/integration/TravelIntegrationTest.java`, `src/test/java/org/triple/backend/travel/unit/entity/TravelItineraryTest.java`, `src/test/java/org/triple/backend/travel/unit/entity/UserTravelItineraryTest.java`, `src/test/java/org/triple/backend/travel/unit/repository/TravelItineraryJpaRepositoryTest.java`, `src/test/java/org/triple/backend/travel/unit/repository/UserTravelItineraryJpaRepositoryTest.java`, `src/test/java/org/triple/backend/group/Integration/GroupIntegrationTest.java`, `src/test/java/org/triple/backend/group/unit/controller/GroupControllerTest.java`, `src/test/java/org/triple/backend/invoice/integration/InvoiceIntegrationTest.java`, `src/test/java/org/triple/backend/invoice/unit/service/InvoiceServiceTest.java`, `src/test/java/org/triple/backend/payment/integration/PaymentIntegrationTest.java`, `src/test/java/org/triple/backend/payment/unit/service/PaymentServiceTest.java`, `src/docs/asciidoc/travel.adoc`, `src/docs/asciidoc/group.adoc` | 엔티티 시그니처 변경 시 회귀 테스트 파급 추적 | 연관 모듈 테스트가 신규 계약 기준으로 통과하고 문서 include가 깨지지 않는다. |
| T4 | 비멤버 사용자 목록 조회 count-only 응답으로 통일 | 로그인 상태라도 그룹 비멤버면 403 대신 count-only 반환 | `src/main/java/org/triple/backend/travel/service/TravelItineraryService.java`, `src/test/java/org/triple/backend/travel/unit/service/TravelItineraryServiceTest.java`, `src/test/java/org/triple/backend/travel/unit/controller/TravelControllerTest.java`, `src/test/java/org/triple/backend/travel/integration/TravelIntegrationTest.java`, `src/docs/asciidoc/travel.adoc` | 권한 실패 응답과 최소 데이터 응답 정책 분리 | 비멤버/비로그인 모두 목록 상세는 숨기고 `count`만 반환하며, 관련 테스트/문서가 동기화된다. |

## 4. 구현 전략

- 설계 선택지 A: 비로그인도 기존 `items` 전체를 조회하되 일부 필드만 마스킹
- 설계 선택지 B: 비로그인은 `count`만 반환하고 `items`는 비워 최소 데이터만 전달
- 최종 선택: B. 사용자 요구사항(숫자만 전달)에 직접 부합하고 비로그인 노출 범위를 최소화한다.

## 5. 테스트 전략

- 단위 테스트: DTO 필드 제거에 따른 직렬화/매핑 및 서비스 분기(`userId == null`) 검증
- 통합 테스트: 비로그인 목록 조회가 200 + `count`를 반환하는지 검증
- 회귀 테스트: 로그인 멤버의 커서 목록 조회(`items`, `nextCursor`, `hasNext`) 동작 유지 검증
- 실행 명령:
  - `./gradlew test --tests "*travel*"`

## 6. 리스크 및 대응

- 리스크 1: `thumbnailUrl` 제거로 테스트 생성자/JSON payload 대량 컴파일 실패 가능
- 대응 1: record 생성자 호출부를 전수 수정하고, DTO 기반 fixture helper를 함께 갱신한다.
- 리스크 2: 비로그인 허용 시 권한 정책 누락으로 private 그룹 데이터 노출 가능
- 대응 2: 서비스에서 그룹 조회 후 그룹 공개 범위/멤버십 조건을 명시적으로 분기한다.

## 7. 질문 유도(학습용)

- Q1. 비로그인 사용자에게 `count`만 노출할 때 정보 최소화 원칙(least privilege)을 어떻게 정의할 수 있을까?
- Q2. 기존 응답 필드 제거가 클라이언트 호환성에 미치는 영향은 어떤 버저닝 전략으로 완화할 수 있을까?
- Q3. 인증 상태 분기 로직을 컨트롤러가 아닌 서비스에 두는 이유는 무엇일까?

## 8. 사용자 피드백 반영 로그

- 피드백 #1: 모든 여행 API의 `thumbnailUrl` 삭제 요청
- 반영 방식: travel 요청/응답 DTO 및 매핑 로직에서 `thumbnailUrl` 제거 TASK(T1)로 분리
- 상태: 반영 완료
- 피드백 #2: 비로그인 여행 목록 조회 시 `TravelItineraryCursorResponseDto`에 `count` 추가 후 숫자 전달 요청
- 반영 방식: 비로그인 분기 + `count` 필드 추가 TASK(T2)로 분리
- 상태: 반영 완료
- 피드백 #3: `TravelItinerary` 엔티티에서도 `thumbnailUrl` 제거 요청
- 반영 방식: T1을 DTO 제거에서 엔티티 필드/시그니처 제거로 확장하고, T3에 연관 모듈 테스트 동기화 범위를 추가
- 상태: 반영 완료
- 피드백 #4: 그룹 멤버가 아니어도 여행 목록 조회 시 count만 표시 요청
- 반영 방식: T4를 추가해 서비스 권한 분기(비멤버 -> count-only)와 테스트/문서를 함께 갱신
- 상태: 반영 완료

## 9. Merge Incident / Rollback Plan

- Merge risk candidates: 목록 조회 인증 정책 변경으로 컨트롤러/서비스/테스트 불일치 가능
- During merge issue rule: 컴파일/테스트 실패 발생 시 PLAN에 이슈 대응 TASK 추가 후 DRAFT 신규 작성, 사용자 확인 후 수정
- Post-merge issue rule: 병합 후 이상 발견 시 해당 TASK 변경을 우선 롤백하고 `PLAN/DRAFT 업데이트 -> 사용자 확인 -> 수정 -> 테스트 -> 재병합` 순서로 진행
