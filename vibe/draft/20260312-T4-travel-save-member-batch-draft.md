# DRAFT: T4 - 여행 생성 멤버 저장 배치 조회 리팩토링

## 0. Meta

- 작성일: 2026-03-12
- TASK ID: T4
- 관련 이슈: #158

## 1. TASK 목표

- 루프 내부 반복 조회(`findById`, `exists`, `save`)를 배치 처리로 전환한다.
- 기존 예외 규칙(USER_NOT_FOUND, SAVE_FORBIDDEN)과 중복/리더 스킵 동작을 유지한다.

## 2. 변경 설계 요약

- UUID 목록 -> userId 집합 변환 (중복 제거, 리더 제외)
- `userJpaRepository.findAllById(...)`로 멤버 유저 일괄 조회
- `userGroupJpaRepository.countByGroupIdAndUserIdInAndJoinStatus(...)`로 그룹 멤버십 일괄 검증
- `userTravelItineraryJpaRepository.saveAll(...)`로 매핑 일괄 저장

## 3. 변경 파일 목록

- `src/main/java/org/triple/backend/travel/service/TravelItineraryService.java`
- `src/main/java/org/triple/backend/group/repository/UserGroupJpaRepository.java`
- `src/test/java/org/triple/backend/travel/unit/service/TravelItineraryServiceTest.java`

## 4. 검증 계획

- `./gradlew test --tests "org.triple.backend.travel.unit.service.TravelItineraryServiceTest" -x jacocoTestCoverageVerification`
- `./gradlew build`
