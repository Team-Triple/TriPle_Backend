# PLAN: 여행 생성 멤버 저장 배치 조회 리팩토링

## 0. Meta

- 작성일: 2026-03-12
- 프로젝트: spring-clone
- 작성자: AI Agent
- 관련 이슈/요청: #158, `addTravelMembers` N+1 조회 제거 및 배치 저장

## 1. JOB 분석

- JOB 목적: 여행 생성 시 멤버 UUID 저장 과정에서 반복 조회를 제거해 성능을 개선한다.
- 기대 결과: 멤버 저장이 `findAllById` + 그룹 멤버십 일괄 검증 + `saveAll` 방식으로 수행된다.
- 범위(In Scope): travel 서비스 멤버 저장 로직, userGroup repository 보조 메서드, service 테스트 보강
- 제외 범위(Out of Scope): 인증 세션 구조 변경, API 스펙 변경

## 2. 현재 상태 진단

- 현재 코드 구조: `for` 루프 내부에서 `findById`, `existsByGroupIdAndUserId...`, `save`가 반복 호출됨
- 재사용 가능한 컴포넌트: `SessionManager.resolveUserId(...)`
- 제약사항: UUID 매핑 실패/비멤버/중복/리더 본인 처리 규칙은 기존과 동일해야 함

## 3. TASK 분해

| TASK ID | 작업명 | 목적 | 대상 파일(예상) | CS 학습 포인트 | 완료 조건(DoD) |
|---|---|---|---|---|---|
| T4 | 멤버 저장 배치 조회화 | N+1 제거 | `TravelItineraryService`, `UserGroupJpaRepository`, `TravelItineraryServiceTest` | 루프 기반 조회를 집합 연산 + 배치 쿼리로 전환 | 루프 내 개별 user/group 조회가 제거되고 전체 빌드가 통과한다. |

## 4. 구현 전략

- 설계 선택지 A: 현재 로직 유지 + 캐시 도입
- 설계 선택지 B: UUID -> userId 변환 후 ID 집합 기준 배치 조회/검증/저장
- 최종 선택: B. 쿼리 수를 예측 가능하게 줄이고 검증 규칙도 유지할 수 있음

## 5. 테스트 전략

- `TravelItineraryServiceTest`에 신규 분기(중복/리더 스킵, UUID 매핑 실패, 비멤버 차단) 확인
- 실행 명령:
  - `./gradlew test --tests "org.triple.backend.travel.unit.service.TravelItineraryServiceTest" -x jacocoTestCoverageVerification`
  - `./gradlew build`

## 6. 리스크 및 대응

- 리스크1: `findAllById` 결과 크기/순서 차이로 사용자 누락 검증 실수 가능
- 대응1: ID 집합 크기와 조회 결과 크기 비교로 누락 검증
- 리스크2: 그룹 멤버십 검증 쿼리 누락으로 비멤버 저장 위험
- 대응2: `countByGroupIdAndUserIdInAndJoinStatus` 결과를 요청 인원과 비교

## 7. 질문 유도(학습용)

- Q1. 배치 조회로 바꿀 때 정확성(검증 규칙)과 성능 중 무엇을 먼저 보장해야 하는가?
- Q2. `count` 기반 멤버십 검증과 `exists` 반복 검증의 트레이드오프는 무엇인가?
- Q3. UUID->ID 변환까지 배치화하려면 auth/session 계층을 어떻게 확장해야 하는가?

## 8. 사용자 피드백 반영 로그

- 피드백#1: `addTravelMembers`의 루프 내부 반복 DB 조회로 N+1 우려
- 반영 방식: 멤버 조회/검증/저장을 배치 쿼리 방식으로 리팩토링
- 상태: 진행중

## 9. Merge Incident / Rollback Plan

- Merge risk candidates: 리팩토링 중 예외 코드/검증 순서가 바뀌어 동작 회귀 가능
- During merge issue rule: 테스트 실패 시 즉시 중단 후 PLAN/DRAFT에 실패 원인 반영
- Post-merge issue rule: 문제 확인 시 해당 커밋 롤백 후 배치 검증 로직만 최소 단위로 재적용
