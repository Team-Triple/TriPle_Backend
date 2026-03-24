# AGENTS.md

이 파일은 이 저장소의 VIBE 코딩 규칙을 다른 환경에서도 동일하게 재사용하기 위한 기준 문서다.

## Source of Truth

- 에이전트 실행 절차/응답 규칙: `vibe/VIBE_PROMPT.md`
- PLAN 템플릿: `vibe/plan/PLAN_TEMPLATE.md`
- DRAFT 템플릿: `vibe/draft/DRAFT_TEMPLATE.md`
- 저장소 공통 정책(게이트/스타일/사고 대응): `AGENTS.md`
- 충돌 시 우선순위: `AGENTS.md` -> `vibe/VIBE_PROMPT.md` -> 템플릿 문서

## Required Flow

- 작업 순서의 단일 기준은 `vibe/VIBE_PROMPT.md`의 `Working Policy`를 따른다.
- PLAN/DRAFT 산출물은 템플릿(`vibe/plan/PLAN_TEMPLATE.md`, `vibe/draft/DRAFT_TEMPLATE.md`) 형식을 따른다.
- 병합 착수/중단/롤백 조건은 아래 `Merge Gate`, `Merge Incident Rule`을 따른다.

## Portability Rule

- 다른 프로젝트로 이 규칙을 옮길 때도 동일한 디렉터리 구조를 유지한다.
- 최소 구조:
    - `AGENTS.md`
    - `vibe/VIBE_PROMPT.md`
    - `vibe/plan/PLAN_TEMPLATE.md`
    - `vibe/draft/DRAFT_TEMPLATE.md`
- 프로젝트 특화 정보(도메인, 아키텍처, 테스트 명령)만 교체하고 프로세스 골격은 유지한다.

## Merge Gate

- PLAN/DRAFT 없이 바로 코드 변경 금지.
- 모든 요청은 PLAN 작성부터 시작한다. (예외 없음)
- 사용자가 PLAN을 확인하고 명시적으로 `작업 진행`이라고 승인하기 전에는 DRAFT 작성 금지.
- 사용자가 DRAFT를 확인하고 명시적으로 `병합!`이라고 승인하기 전에는 코드 반영/병합 금지.
- 사용자 승인 없는 병합 금지.
- 테스트 실패 상태 병합 금지.
- DRAFT의 `변경 파일 목록`에 있는 파일이 `변경 전 코드`/`코드 제안`에 모두 없으면 병합 금지.
- 신규/수정 파일이 1개라도 누락되면 DRAFT를 먼저 보완한 뒤 진행.

## Explicit Command Gate

- Gate 1 (PLAN): 어떤 작업이든 PLAN이 먼저 있어야 한다.
- Gate 2 (DRAFT): 사용자 `작업 진행` 명령이 있어야 DRAFT를 작성한다.
- Gate 3 (CODE/MERGE): 사용자 `병합!` 명령이 있어야 실제 코드 반영을 시작한다.
- 위 3개 게이트를 충족하지 못하면 에이전트는 다음 단계로 진행하지 않고 사용자 확인을 요청한다.

## Commit Rule

- 커밋은 작업 단위(TASK 단위)로 분리한다.
- 서로 다른 TASK의 변경을 하나의 커밋에 혼합하지 않는다.
- 커밋 전 해당 TASK 범위 테스트를 실행하고 결과를 기록한다.
- 병합 후 커밋 단계에 진입하면 사용자에게 이슈번호를 반드시 확인한다.
- 이슈번호 확인 전에는 커밋을 진행하지 않는다. (예외 없음)
- 모든 커밋 메시지 제목은 `[#이슈번호]` 형식으로 시작한다.

## DRAFT Lifecycle Rule

- 해당 JOB의 마지막 TASK 병합이 완료될 때까지 DRAFT 파일은 삭제하지 않고 유지한다. (무조건, 예외 없음)
- 해당 JOB의 마지막 TASK 병합이 완료되면 그 JOB의 DRAFT 파일들을 일괄 삭제한다. (무조건, 예외 없음)
- 병합 중 이슈 대응으로 신규 TASK가 생기면 이슈 대응 DRAFT도 마지막 TASK 병합 완료 시점까지 함께 유지한다.

## PLAN Lifecycle Rule

- 해당 JOB의 마지막 TASK가 병합되고 커밋까지 완료되면 해당 PLAN 파일은 즉시 삭제한다. (무조건, 예외 없음)
- 커밋이 아직 완료되지 않았다면 PLAN 파일은 유지한다.

## Code Style

- 3항 연산자는 가독성이 명확히 좋아지는 경우가 아니면 지양한다.
- 조건 분기는 기본적으로 `if/else`를 우선 사용한다.

## Merge Incident Rule (추가)

- 병합 과정에서 새로운 문제(주입 경로 불일치, 컴파일/테스트 실패, 인코딩 오류, 설계 충돌 등)를 발견하면 즉시 병합 작업을 중지한다.
- 문제를 발견한 즉시 PLAN에 해당 이슈 대응용 TASK를 신규 추가한다.
- 추가한 이슈 대응 TASK마다 DRAFT를 신규 작성하고, 원인/수정 방향/검증 방법/변경 파일 목록을 명시한다.
- 이슈 대응 TASK의 PLAN/DRAFT에 대해 사용자 확인을 받기 전에는 코드 수정 또는 재병합을 진행하지 않는다.
- 문제 내용, 원인, 수정 방향을 PLAN/DRAFT에 먼저 반영하고 사용자 확인을 받은 뒤에만 수정/재병합한다.
- 이미 병합한 뒤 문제가 발견되면, 해당 TASK에서 병합한 변경을 먼저 롤백한 다음 `PLAN/DRAFT 업데이트 -> 사용자 확인 -> 수정 -> 테스트 -> 재병합` 순서로 진행한다.
- 위 규칙은 예외 없이 적용한다.
