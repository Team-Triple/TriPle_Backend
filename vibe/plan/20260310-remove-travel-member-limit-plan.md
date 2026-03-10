# PLAN: travel 도메인 travel-member-limit 제거

## 0. Meta

- 작성일: 2026-03-10
- 프로젝트: spring-clone
- 작성자: AI Agent
- 관련 이슈/요청: travel 도메인에서 `travel-member-limit` 제거 및 연관 DTO/쿼리/테스트 동기화

## 1. JOB 분석

- JOB 목적: Travel 일정의 정원(`memberLimit`) 개념을 제거해 도메인/요청/응답/검증 로직을 단순화한다.
- 기대 결과: Travel 생성/수정/조회/참여 흐름에서 `memberLimit` 필드와 검증/에러가 사라지고 테스트가 모두 통과한다.
- 범위(In Scope): travel 엔티티/DTO/서비스/에러코드, travel 조회 응답, group 상세의 recent travel 응답, 관련 테스트
- 제외 범위(Out of Scope): group 도메인의 `group.memberLimit` 정책 및 그룹 생성/수정 API 계약

## 2. 현재 상태 진단

- 현재 코드 구조: `travel.entity.TravelItinerary`가 `memberLimit`을 소유하고 create/update/join에서 제한 검증 수행
- 재사용 가능 컴포넌트: 기존 `memberCount` 증가/감소, 권한 검증, 커서 페이징 로직
- 제약사항: `memberLimit`은 group 상세 recent travel DTO까지 전파되어 교차 도메인 수정이 필요

## 3. TASK 분해

| TASK ID | 작업명 | 목적 | 대상 파일(예상) | CS 학습 포인트 | 완료 조건(DoD) |
|---|---|---|---|---|---|
| T1 | Travel 도메인 memberLimit 제거 | 엔티티/요청/수정/참여 로직에서 정원 개념 제거 | `travel/entity/TravelItinerary.java`, `travel/dto/request/*`, `travel/service/TravelItineraryService.java`, `travel/exception/TravelItineraryErrorCode.java` | 도메인 제약 제거 시 불변식 재정의(상한 제약 제거) | Travel 코드에서 `memberLimit` 참조가 제거되고 컴파일된다. |
| T2 | 응답 계약 동기화 | travel 조회 및 group 상세의 recent travel 응답에서 필드 제거 | `travel/dto/response/TravelItineraryCursorResponseDto.java`, `group/dto/response/GroupDetailResponseDto.java`, `group/service/GroupService.java` | API 계약 변경의 파급 범위 추적 | 응답 DTO/매핑에서 `memberLimit` 필드가 제거된다. |
| T3 | 테스트/문서 스펙 정리 | 요청 JSON/응답 검증/REST Docs를 새 계약에 맞춤 | `travel/integration/TravelIntegrationTest.java`, `travel/unit/controller/TravelControllerTest.java`, `travel/unit/entity/TravelItineraryTest.java`, `travel/unit/repository/TravelItineraryJpaRepositoryTest.java`, `travel/unit/repository/UserTravelItineraryJpaRepositoryTest.java`, `travel/unit/service/TravelItineraryServiceTest.java`, `group/Integration/GroupIntegrationTest.java`, `group/unit/controller/GroupControllerTest.java` | 계약 기반 테스트 유지보수 | travel/group 관련 테스트가 새 스펙으로 통과한다. |
| T4 | 프로세스 규칙 강화(이슈번호 필수) | 커밋 전 이슈번호 확인 및 커밋 메시지 형식 강제 | `AGENTS.md`, `vibe/VIBE_PROMPT.md` | 변경관리 메타데이터 추적성(Traceability) | 규칙 문서에 이슈번호 확인/표기 규칙이 명시된다. |

## 4. 구현 전략

- 설계 선택지 A: `memberLimit` 필드를 유지하고 무제한 값(예: 9999)으로 우회
- 설계 선택지 B: `memberLimit` 필드와 관련 검증/에러를 완전 제거
- 최종 선택: B. 요청사항이 "삭제"이며, 우회값 방식은 불필요 상태와 혼란을 남긴다.

## 5. 테스트 전략

- 단위 테스트: TravelItinerary 엔티티 생성/수정/참여/이탈 로직에서 `memberLimit` 제거 후 동작 확인
- 통합 테스트: 여행 생성/수정/조회 API의 request/response contract 검증
- 회귀 테스트: group 상세 API의 `recentTravels` 응답 구조 변경 반영
- 대상 테스트 파일:
  - `src/test/java/org/triple/backend/travel/integration/TravelIntegrationTest.java`
  - `src/test/java/org/triple/backend/travel/unit/controller/TravelControllerTest.java`
  - `src/test/java/org/triple/backend/travel/unit/entity/TravelItineraryTest.java`
  - `src/test/java/org/triple/backend/travel/unit/repository/TravelItineraryJpaRepositoryTest.java`
  - `src/test/java/org/triple/backend/travel/unit/repository/UserTravelItineraryJpaRepositoryTest.java`
  - `src/test/java/org/triple/backend/travel/unit/service/TravelItineraryServiceTest.java`
  - `src/test/java/org/triple/backend/group/Integration/GroupIntegrationTest.java`
  - `src/test/java/org/triple/backend/group/unit/controller/GroupControllerTest.java`
- 실행 명령:
  - `./gradlew test --tests "*travel*"`
  - `./gradlew test --tests "*group*"`

## 6. 리스크 및 대응

- 리스크 1: group 상세 응답의 nested DTO 변경 누락으로 테스트 실패
- 대응 1: `GroupService` + `GroupDetailResponseDto` + group controller/integration 테스트를 함께 수정
- 리스크 2: TravelItineraryErrorCode 상수 제거 후 테스트/서비스 참조 누락
- 대응 2: `TRAVEL_MEMBER_LIMIT_EXCEEDED` 전역 검색 후 참조 제거 또는 시나리오 대체

## 7. 질문 유도(학습용)

- Q1. 정원 제한 제거 시 동시성 충돌 처리에서 낙관락/비관락의 역할은 무엇이 남는가?
- Q2. API contract에서 필드 제거가 하위호환성을 깨는 경우 어떤 버저닝 전략이 적합한가?
- Q3. 도메인 불변식 축소가 장기 유지보수성에 미치는 영향은?

## 8. 사용자 피드백 반영 로그

- 피드백 #1: travel 도메인의 travel-member-limit 삭제 및 DTO/쿼리 수정 요청
- 반영 방식: travel + 연관 group recent travel 응답까지 영향 범위 확장해 계획 수립
- 상태: 반영 완료
- 피드백 #2: T1 단독 병합 시 컴파일 실패(`getMemberLimit` 참조 잔존)
- 반영 방식: T2(응답 계약 동기화)를 병행 반영해야 재병합 가능하도록 merge incident 반영
- 상태: 반영 완료
- 피드백 #3: T2 반영 후 테스트 컴파일 실패(56건) 발생
- 반영 방식: T3 범위를 travel/group 외 `invoice`/`payment` 테스트의 `TravelItinerary` 생성자 사용부까지 확장
- 상태: 반영 완료
- 피드백 #4: TASK 분리 커밋과 `[#이슈번호]` 강제 규칙 추가 요청
- 반영 방식: T4 추가 후 AGENTS/VIBE 규칙에 이슈번호 확인 + 커밋 메시지 형식 강제
- 상태: 반영 완료

## 9. Merge Incident / Rollback Plan

- Merge risk candidates: 테스트 계약(JSON path/REST Docs) 대량 수정으로 누락 가능
- During merge issue rule: 실패 원인과 수정 범위를 PLAN/DRAFT에 먼저 업데이트 후 사용자 확인
- Post-merge issue rule: 문제 발생 시 해당 TASK 변경을 우선 롤백하고 수정안 확정 후 재병합
