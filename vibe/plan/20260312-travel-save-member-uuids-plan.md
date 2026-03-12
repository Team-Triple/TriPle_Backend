# PLAN: 여행 일정 생성 시 추가 멤버 UUID 저장

## 0. Meta
- 작성일: 2026-03-12
- 프로젝트: TriPle_Backend
- 이슈: #158
- 상태: 완료

## 1. 요청 요약
- 여행 일정 생성자는 기존처럼 세션 `userId`(여행장)로 처리한다.
- 요청 DTO로 추가 멤버 `List<UUID>`를 받는다.
- `auth/session` UUID 해석 로직으로 UUID를 내부 `userId(Long)`로 매핑해 `UserTravelItinerary`를 함께 저장한다.

## 2. 반영 범위
- `TravelItinerarySaveRequestDto`
- `TravelItineraryService`
- 생성 관련 단위 테스트

## 3. 구현 결정
- DTO에 `memberUuids` 필드 추가
- 서비스에서 `SessionManager.resolveUserId(...)`로 멤버 UUID 매핑
- 여행장 본인/중복 UUID는 저장 제외
- 멤버 유저/그룹 소속 검증 후 `UserTravelItinerary(MEMBER)` 저장
- 멤버 저장 시 `memberCount` 증가

## 4. 검증
- `./gradlew compileTestJava -q` 통과
- `./gradlew test --tests "*travel*" -x jacocoTestCoverageVerification` 통과

## 5. 비고
- 사용자 요청에 따라 DRAFT 파일은 모두 삭제했다.
