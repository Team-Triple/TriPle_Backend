# AGENT_TEMPLATE

아래 내용을 각 프로젝트의 `AGENTS.md`로 복사하여 사용한다.

## 핵심 워크플로우 (Core Workflow)

1. JOB을 분석한다.
2. JOB을 여러 개의 TASK로 분해한다.
3. PLAN 마크다운을 `vibe/plan/` 디렉토리에 저장한다.
4. 사용자와의 Q&A 및 설계 피드백을 반영한다.
5. TASK DRAFT 마크다운을 `vibe/draft/` 디렉토리에 저장한다.
6. 사용자 리뷰를 기다린다.
7. 명시적인 merge 요청이 있기 전까지 코드베이스에 병합하지 않는다.
8. 병합 전/후 테스트를 실행하고 결과를 보고한다.

## 파일 정책 (File Policy)

- PLAN 템플릿: `vibe/plan/PLAN_TEMPLATE.md`
- DRAFT 템플릿: `vibe/draft/DRAFT_TEMPLATE.md`
- 프롬프트 기본 파일: `vibe/VIBE_PROMPT.md`

## 가드레일 (Guardrails)

- PLAN + DRAFT 없이 코드 직접 병합 금지
- 사용자 승인 없이 병합 금지
- 테스트 실패 상태에서 병합 금지

## 학습 정책 (Learning Policy)

- 모든 TASK에는 최소 하나 이상의 CS 개념이 포함되어야 한다.
- 모든 DRAFT에는 면접 대비용 학습 포인트를 포함해야 한다.