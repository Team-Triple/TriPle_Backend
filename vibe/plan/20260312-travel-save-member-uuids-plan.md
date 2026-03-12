# PLAN: 여행 일정 생성 시 멤버 UUID 목록 저장

## 0. Meta

- 작성일: 2026-03-12
- 프로젝트: spring-clone
- 작성자: AI Agent
- 관련 이슈/요청: #158, 여행 일정 생성 시 생성자는 세션 userId를 유지하고 추가 멤버는 `List<UUID>`로 받아 저장

## 1. JOB 분석

- JOB 목적: 여행 생성 API의 생성자 식별은 기존 세션 기반으로 유지하면서, 추가 멤버를 UUID 목록으로 한 번에 등록한다.
- 기대 결과: `POST /travels` 요청에서 `memberUuids`를 받아 `auth/session` UUID 해석 로직으로 userId 매핑 후 `UserTravelItinerary(MEMBER)`를 저장한다.
- 범위(In Scope): travel 생성 DTO/서비스 변경, 생성 관련 단위 테스트 정합성 반영
- 제외 범위(Out of Scope): 여행 참가/탈퇴/수정/삭제 API 계약 변경, DB 스키마 변경

## 2. 현재 상태 진단

- 현재 코드 구조: `TravelItineraryService.saveTravels(dto, userId)`에서 여행장(`LEADER`) 저장 후 여행 일정 생성을 처리한다.
- 재사용 가능한 컴포넌트: `SessionManager.resolveUserId(Object)`를 통해 UUID -> Long userId 해석 가능
- 제약사항: UUID 매핑 실패/그룹 비멤버/중복 멤버 처리 규칙을 생성 트랜잭션 내에서 일관되게 처리해야 한다.

## 3. TASK 분해

| TASK ID | 작업명 | 목적 | 대상 파일(예상) | CS 학습 포인트 | 완료 조건(DoD) |
|---|---|---|---|---|---|
| T1 | 생성 DTO 확장 | 추가 멤버 UUID 목록 수신 | `src/main/java/org/triple/backend/travel/dto/request/TravelItinerarySaveRequestDto.java` | 외부 식별자(UUID)와 내부 식별자(Long) 분리 | 생성 DTO가 `memberUuids`를 수신하고 기존 테스트 생성자 호출과 호환된다. |
| T2 | 생성 서비스 멤버 저장 로직 추가 | UUID 매핑 후 멤버 매핑 저장 | `src/main/java/org/triple/backend/travel/service/TravelItineraryService.java` | 트랜잭션 내 다중 엔티티 저장/검증 흐름 | 여행장 저장 후 멤버 UUID 목록이 검증/매핑되어 `MEMBER`로 저장되고 `memberCount`가 반영된다. |
| T3 | 테스트 정합성 반영 | DTO/서비스 변경에 맞춘 테스트 보정 | `src/test/java/org/triple/backend/travel/unit/service/TravelItineraryServiceTest.java`, `src/test/java/org/triple/backend/travel/unit/entity/TravelItineraryTest.java`, `src/test/java/org/triple/backend/travel/unit/repository/TravelItineraryJpaRepositoryTest.java`, `src/test/java/org/triple/backend/travel/unit/repository/UserTravelItineraryJpaRepositoryTest.java` | 계약 변경 시 테스트 안정화 | travel 범위 테스트가 모두 통과한다. |

## 4. 구현 전략

- 설계 선택지 A: DTO에서 생성자 UUID까지 받아 서비스에서 단일 UUID를 여행장으로 사용
- 설계 선택지 B: 생성자는 기존 세션 userId를 유지하고 DTO는 추가 멤버 UUID 목록만 수신
- 최종 선택: B. 기존 인증/인가 흐름을 유지하면서 요구사항(여행장 유지 + 추가 멤버 등록)을 정확히 충족한다.

## 5. 테스트 전략

- 단위 테스트: DTO 생성자 호환성, 서비스 생성 흐름 회귀 여부 확인
- 통합/회귀 테스트: travel 도메인 전체 테스트 패턴으로 회귀 확인
- 실행 명령:
  - `./gradlew compileTestJava -q`
  - `./gradlew test --tests "*travel*" -x jacocoTestCoverageVerification`

## 6. 리스크 및 대응

- 리스크1: UUID 매핑 실패/그룹 비멤버 처리 중간 예외로 부분 저장 우려
- 대응1: `@Transactional` 범위 내에서 `BusinessException(RuntimeException)`으로 일괄 롤백 보장
- 리스크2: DTO 시그니처 변경으로 기존 테스트/픽스처 대량 컴파일 에러 발생
- 대응2: DTO 보조 생성자(기존 5-인자) 유지 + 필요한 테스트만 점진 반영

## 7. 질문 유도(학습용)

- Q1. 생성자 식별은 세션 기반으로 유지하고 멤버만 UUID 입력받을 때 보안/무결성 이점은 무엇인가?
- Q2. UUID 목록 저장에서 중복 UUID/자기 자신 UUID를 무시하는 정책의 장단점은 무엇인가?
- Q3. 멤버 저장 실패 시 전체 롤백이 필요한 이유를 트랜잭션 관점에서 설명할 수 있는가?

## 8. 사용자 피드백 반영 로그

- 피드백#1: 생성자 userId는 그대로 유지하고, 다른 여행 멤버 `List<UUID>`를 받도록 변경
- 반영 방식: DTO를 `memberUuids` 중심으로 변경하고 서비스에서 세션 userId + 멤버 UUID 목록 처리로 재구성
- 상태: 반영 완료
- 피드백#2: DRAFT 파일 삭제 요청
- 반영 방식: 작업 DRAFT 파일 전체 삭제
- 상태: 반영 완료
- 피드백#3: PLAN 형식을 기존 문서 스타일로 재정리 요청
- 반영 방식: 템플릿 기반(0~9 섹션)으로 PLAN 재작성
- 상태: 반영 완료

## 9. Merge Incident / Rollback Plan

- Merge risk candidates: 생성 로직 변경으로 테스트/문서와 API 계약 불일치 가능
- During merge issue rule: 문제 발견 시 병합 중지, PLAN/TASK 갱신 후 사용자 확인 뒤 수정
- Post-merge issue rule: 병합 후 문제 발견 시 해당 변경 롤백 후 `PLAN 갱신 -> 확인 -> 수정 -> 테스트 -> 재병합` 순서 준수
