---
name: qa-on-device
description: Use when you need to self-QA an implemented feature against its requirement/analysis document on an Android device (emulator or physical device) with Maestro. Writes a Korean QA sheet before running (what will be verified), verifies UI flows, writes a bug document for every confirmed app bug, and runs a QA -> fix proposal -> user approval -> QA loop. Make sure to use this skill whenever the user asks to QA, verify, smoke-test, or "run through the scenarios" on a device or emulator, even if they do not mention Maestro. Output must be in Korean.
---

# QA on Device (Emulator / Physical Device)

## Overview
Generates a structured **Korean QA sheet** from requirements or analysis docs, verifies UI flows on an Android device (emulator or connected physical device) via batch Maestro execution, and fills the results back into the sheet.

All QA documents live under **`docs/qa/`**:
- `docs/qa/qa-sheet.md`: the QA sheet. Saved **before** execution as the verification plan (all `대기(PENDING)`), then filled with results.
- `docs/qa/bugs/BUG-NNN-<slug>.md`: one bug document per confirmed app bug.

When an app bug is confirmed, the run continues into the loop **QA → fix proposal → user approval → QA** (Step 7). App code is never changed during a QA run, and never without the user's approval.
**Token cost is the main constraint**: write flows once in a temporary directory, run them in batch, triage failures only, and never explore the screen step-by-step.

## When to Use
- Verifying an implemented feature on an Android device (emulator or physical device) after coding.
- Running smoke tests or acceptance criteria against requirement documents (`docs/*-analysis.md`, `docs/implementation_plan.md`).
- When the user asks to QA, smoke-test, or verify scenarios on a device or emulator.

## Preconditions (Fail-Fast)
Check once in a single command; stop immediately with actionable guidance if any check fails:
1. `adb devices`: Exactly one device (emulator or physical device) in `device` state.
   - If no device is detected: instruct the user to connect a physical device (enable USB debugging) or launch an emulator, then stop.
   - If multiple devices are connected: instruct the user to leave only the target device connected or specify target via `export ANDROID_SERIAL=<id>`, then stop.
2. `maestro --version`: Must be installed (if missing: `brew install mobile-dev-inc/tap/maestro`).
3. Read `applicationId` from `app/build.gradle.kts`.

---

## Workflow

### 1. Extract Requirements & Save the QA Sheet (Plan)
- Read analysis docs (`docs/*-analysis.md`, `docs/implementation_plan.md`) or inspect recent commits/code changes if no doc exists.
- Draft a QA test sheet in **Korean** with the standard 7 columns:
  `ID | 기능/화면 | 테스트 시나리오 | 기대 결과 | 검증 방식 | 결과 | 비고`
- Classify `검증 방식`:
  - `UI(Maestro)`: User-visible flows, screen transitions, toggle states.
  - `단위테스트`: Limits/edge cases (debounce, race conditions, server errors).
  - `수동`: Physical device interactions (hardware sensors, biometric auth).
- Initial `결과` is `대기(PENDING)`.
- **Save the draft to `docs/qa/qa-sheet.md` before running anything**, so the user can see what will be verified. The file starts with a header (`대상 브랜치`, `기준 문서`, `검증 환경`) and ends with a `검증하지 않는 범위` list (what this QA will not cover and why).
- If `docs/qa/qa-sheet.md` already exists for a different feature, ask the user before overwriting.

### 2. Map Identifiers from Source (No Screenshots/UI Dumps)
Do NOT dump UI hierarchy or take screenshots to discover elements. Static-grep feature sources instead:
- Visible strings: `Text(`, `label =`, `placeholder =`, `stringResource(`, `strings.xml`.
- Accessibility / Tags: `contentDescription`, `Modifier.testTag`.
- Maestro matches both visible text and `contentDescription`. For dynamic buttons (e.g. "찜하기" <-> "찜 해제"), assert `contentDescription` directly.
- Add `Modifier.testTag` only when no stable text or description exists, strictly following `compose-ui.md`.

### 3. Write Ephemeral Flows (/tmp/maestro-flows/)
- Save all YAML flows into `/tmp/maestro-flows/` (kept until the Step 7 loop ends, then removed).
- One flow per scenario group, each independent with its own `launchApp`.
- Assertions: `assertVisible` / `assertNotVisible`. Use `extendedWaitUntil` (timeout: 10000-15000) after network operations.
- After `inputText`, immediately `assertVisible` the typed text to catch input-drop or state-sync bugs before waiting for results.
- Take screenshots only on persistent failures, never on the happy path.

```yaml
appId: <applicationId>
---
- launchApp:
    clearState: true
- extendedWaitUntil:
    visible: "<expected text after load>"
    timeout: 15000
- tapOn: "<button text or contentDescription>"
- assertVisible: "<expected result>"
```

### 4. Build, Install, Run
```bash
./gradlew installDebug -q
maestro test /tmp/maestro-flows/ 2>&1 | grep -vE '^\s*$|╭|╰|│' | tail -40
```
- If compilation fails, stop and report the build failure without modifying app code.
- Read only pass/fail lines from `maestro test` to save tokens.

### 5. Triage Failures Only (No App Mutation During QA)
For failed flows only:
1. Identify failed step: `grep -E 'COMPLETED|FAILED' ~/.maestro/tests/<latest>/maestro.log | tail`.
2. Re-run that single flow once. If it passes, classify as **Flaky** and note the cause (network latency, rate limit). Flaky results are never fix targets.
3. If still failing, inspect at most 1 screenshot from `~/.maestro/tests/<latest>/` or check filtered logcat: `adb logcat -d | grep -i <applicationId>`.
4. **No mutation during QA**: do not modify application source code while running or triaging. A failure confirmed as an app bug is recorded as `실패(FAIL)` with a `BUG-NNN` reference and gets a bug document (Step 7a). Only modify YAML flows if the test script itself had selector or timing errors, and never weaken an assertion to make a flow pass.

### 6. Fill the QA Sheet & Report (Korean)
- Update `docs/qa/qa-sheet.md` in place with the results (`성공(PASS)`, `실패(FAIL)`, `단위테스트 대상`, `건너뜀`). Failed rows link their bug document in `비고` (e.g. `BUG-001`).
- Sections of the sheet:
  1. **검증 요약**: Total, PASS, FAIL (App Bug / Flaky), NOT VERIFIED.
  2. **QA 테스트 시트 상세**: 7 standard columns filled with results.
  3. **발견된 결함 및 권고사항**: Bug list (ID, title, link to the bug document) and recommended unit tests.
  4. **수정 이력**: Filled during Step 7 (`라운드 | 결함 ID | 수정 내용 | 재검증 결과`).
  5. **검증하지 않는 범위**.
- If there is no `실패(FAIL)` caused by an app bug: clean up `/tmp/maestro-flows/` and finish.

### 7. Bug Document → Fix Proposal → Approval → QA (Loop)
Runs only when Step 5 confirmed at least one app bug.

**7a. Write a bug document** for each confirmed bug at `docs/qa/bugs/BUG-NNN-<slug>.md` (NNN continues from existing files; Korean). Read the relevant code first to find the cause; cite `file:line`.

```markdown
# BUG-001: <한 줄 제목>

- 상태: 제안됨 | 승인됨 | 수정됨 | 재검증 통과 | 반려
- 관련 시나리오: TC-003
- 발견 일시 / 환경: <날짜>, <기기>, <브랜치>

## 증상
- 기대 결과:
- 실제 결과:

## 재현 절차
1.
2.

## 증거
- Maestro 로그: `~/.maestro/tests/<timestamp>/...` (실패 단계)
- 스크린샷 / logcat 발췌 (필요한 경우에만 1개)

## 원인 분석
- `path/File.kt:NN`: <원인>
- 확신 수준: 확정 | 추정 (추정이면 근거와 확인 방법)

## 수정 제안
- 변경 요지:
- 영향 범위 / 위험:
- 재검증 플로우: <TC-ID 목록> + 전체 회귀

## 수정 이력
| 라운드 | 변경 내용 | 재검증 결과 |
|---|---|---|
```

**7b. Propose, then wait.** Summarize each bug (ID, cause, proposed change, risk) in Korean and ask the user per bug: 승인 / 수정 요청 / 건너뜀. **Do not edit application code before approval.** `단위테스트` rows are proposal-only; never auto-applied.

**7c. Fix approved bugs only.** Minimal change; read the matching `.agents/conventions/` files first; no unrelated refactors; do not commit unless asked. Set the bug document status to `승인됨` → `수정됨` and add the change to its `수정 이력`.

**7d. Re-run QA.** Rebuild and install (Step 4) and run **all** flows (regression), not only the failed one. Update the QA sheet (`수정 이력`, results; mark fixed rows as `성공(PASS, 수정 후 재검증)`), and set the bug document status to `재검증 통과`.

**Loop.** Return to Step 5 for any remaining or new failures, and propose again (7a-7d). Stop when:
- no app-bug `실패(FAIL)` remains,
- the user declines or stops,
- the same bug still fails after 2 fix attempts (report that the cause needs re-analysis and stop),
- 3 rounds have run.
Then clean up `/tmp/maestro-flows/` (unless the user asked to keep them) and report in Korean.

---

## Limits (Do Not Force into UI Flows)
Classify these as `단위테스트` instead of Maestro UI flows:
- Timing-dependent logic (debounce interval, polling interval, animation duration).
- Request cancellation and race conditions.
- Simulated backend failures (HTTP 403, 429, timeout) - verify via Fake/Mock DataSource unit tests.

---

## Red Flags (STOP and Start Over)
- Dumping UI hierarchy or taking repeated screenshots to discover screen elements.
- Modifying application source code during a QA run, or at any time without the user's approval of that specific fix.
- Weakening or removing an assertion to make a flow pass.
- Marking unverified or failed scenarios as `성공(PASS)`.
- Reporting an app bug without a bug document under `docs/qa/bugs/`.
- Running flows before the QA sheet exists in `docs/qa/qa-sheet.md`.
- Leaving ephemeral YAML flow files in the repo root or tracking them in git.
- Writing QA documents or the final report in English (must be Korean).

---

## Example

### Source Identifiers (Compose)
`app/src/main/kotlin/.../SearchScreen.kt`:
```kotlin
TextField(
    value = query,
    placeholder = { Text("저장소 검색") },
    ...
)
IconButton(
    onClick = { onBookmarkClick(repo) },
    modifier = Modifier.semantics {
        contentDescription = if (repo.isBookmarked) "북마크 해제" else "북마크 추가"
    }
) { ... }
```

### Flow (`/tmp/maestro-flows/tc-001-bookmark.yaml`)
```yaml
appId: com.nlab.androidboot
---
- launchApp:
    clearState: true
- extendedWaitUntil:
    visible: "저장소 검색"
    timeout: 10000
- tapOn: "저장소 검색"
- inputText: "android"
- assertVisible: "android"
- extendedWaitUntil:
    visible: "북마크 추가"
    timeout: 15000
- tapOn: "북마크 추가"
- assertVisible: "북마크 해제"
```

### QA Sheet (`docs/qa/qa-sheet.md`)
```markdown
# 기능 검증 QA 시트: 저장소 검색 및 북마크

- 대상 브랜치: feature/sample4
- 기준 문서: docs/03-github-repo-explorer-analysis.md
- 검증 환경: emulator-5554, Maestro 2.12.0

## 1. 검증 요약
- 총 시나리오: 4건
- 성공(PASS): 2건
- 실패(FAIL): 1건 (앱 버그)
- 단위테스트 대상: 1건

## 2. QA 테스트 시트 상세
| ID | 기능/화면 | 테스트 시나리오 | 기대 결과 | 검증 방식 | 결과 | 비고 |
|---|---|---|---|---|---|---|
| TC-001 | 검색 화면 | 'android' 키워드로 저장소 검색 | 검색 결과 목록 노출 | UI(Maestro) | 성공(PASS) | |
| TC-002 | 북마크 | 첫 번째 아이템의 북마크 버튼 탭 | 버튼 라벨이 '북마크 해제'로 변경 | UI(Maestro) | 성공(PASS) | contentDescription 토글 검증 |
| TC-003 | 검색 입력 | 빠른 연속 텍스트 입력 ('test') | 입력창에 'test'가 온전히 유지됨 | UI(Maestro) | 실패(FAIL) | BUG-001: 입력값이 잘림 |
| TC-004 | 검색 디바운스 | 입력 멈춤 후 300ms 뒤 API 호출 | 300ms 이내 중복 호출 없음 | 단위테스트 | 단위테스트 대상 | ViewModel 테스트 권장 |

## 3. 발견된 결함 및 권고사항
- [BUG-001](bugs/BUG-001-search-input-truncated.md): 빠른 입력 시 검색어가 잘림 (TC-003)
- 권고 단위테스트: TC-004

## 4. 수정 이력
| 라운드 | 결함 ID | 수정 내용 | 재검증 결과 |
|---|---|---|---|
| 1 | BUG-001 | debounce 적용, 필드 되돌려 쓰기 제거 | 성공(PASS, 수정 후 재검증) |

## 5. 검증하지 않는 범위
- 프로세스 종료 후 복원, IME 조합 입력, 실패 응답 경로
```
