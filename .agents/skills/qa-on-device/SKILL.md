---
name: qa-on-device
description: Use when you need to self-QA an implemented feature against its requirement/analysis document on an Android device (emulator or physical device) with Maestro. Writes a Korean QA sheet before running (what will be verified), verifies UI flows, separates unstable (Flaky) results with a recovery plan, writes a bug document for every confirmed app bug, and runs a QA -> fix proposal -> user approval -> QA loop. Make sure to use this skill whenever the user asks to QA, smoke-test, or "run through the scenarios" of a feature on a device or emulator, even if they do not mention Maestro. Output must be in Korean.
---

# QA on Device (Emulator / Physical Device)

## Overview
Generates a structured **Korean QA sheet** from requirements or analysis docs, verifies UI flows on an Android device (emulator or connected physical device) via batch Maestro execution, and fills the results back into the sheet.

All QA documents live under **`docs/qa/`**:
- `docs/qa/qa-sheet.md`: the QA sheet. Saved **before** execution as the verification plan (all `대기(PENDING)`), then filled with results.
- `docs/qa/bugs/BUG-NNN-<slug>.md`: one bug document per confirmed app bug.

When an app bug is confirmed, the run continues into the loop **QA → fix proposal → user approval → QA** (Step 8). App code is never changed during a QA run, and never without the user's approval of that specific change.
**Token cost is the main constraint**: write flows once in a temporary directory, run them in batch, triage failures only, and never explore the screen step-by-step.

## When to Use
- Verifying an implemented feature on an Android device (emulator or physical device) after coding.
- Running smoke tests or acceptance criteria against requirement documents (`docs/*-analysis.md`, `docs/implementation_plan.md`).
- When the user asks to QA, smoke-test, or verify scenarios of a feature on a device or emulator.

## Result Values
Use exactly these values in the `결과` column and in the summary counts:
- `대기(PENDING)`: planned, not run yet.
- `성공(PASS)`: passed stably. `성공(PASS, 수정 후 재검증)`: failed before, passed after an approved fix.
- `실패(FAIL)`: reproducible failure (app bug, or unconfirmed cause pending the user's decision).
- `Flaky(불안정)`: passes sometimes and fails sometimes with no crash evidence. Counted separately, never inside PASS or FAIL (see Step 5a).
- `단위테스트 대상`: not verifiable through UI (see Limits).
- `수동 확인 필요`: needs physical interaction (hardware sensors, biometric auth).
- `건너뜀`: not run, with the reason in `비고` (e.g. no usable identifier, build failure).

## Preconditions (Fail-Fast)
Check once in a single command; stop immediately with actionable guidance if any check fails:
1. `adb devices`: exactly one device (emulator or physical device) in `device` state.
   - No device: ask the user to connect a physical device (enable USB debugging) or launch an emulator, then stop.
   - Multiple devices: ask the user which serial to use, then pass it explicitly to every command: `maestro --udid <id> test ...`, `adb -s <id> ...`, and `ANDROID_SERIAL=<id> ./gradlew installDebug` (shell state does not persist between calls, so do not rely on `export`).
2. `maestro --version`: must be installed (if missing: `brew install mobile-dev-inc/tap/maestro`).
3. Read `applicationId` from `app/build.gradle.kts`.

If the device goes `offline` or disconnects mid-run, that is an **infrastructure error, not a failure**: stop, tell the user to reconnect, and re-run the whole batch. Never record it as `실패(FAIL)`.

---

## Workflow

### 1. Extract Requirements & Save the QA Sheet (Plan)
- Read analysis docs (`docs/*-analysis.md`, `docs/implementation_plan.md`) once. Keep user-visible behavior only.
- If there is no analysis doc, infer scenarios from recent commits/code changes, write `기준 문서: 없음(커밋 기준)` in the header, save the sheet, and ask the user to confirm the scope before running.
- Draft a QA test sheet in **Korean** with the standard 7 columns:
  `ID | 기능/화면 | 테스트 시나리오 | 기대 결과 | 검증 방식 | 결과 | 비고`
- Classify `검증 방식`:
  - `UI(Maestro)`: user-visible flows, screen transitions, toggle states.
  - `단위테스트`: limits/edge cases (see Limits).
  - `수동`: physical device interactions.
- Initial `결과` is `대기(PENDING)`.
- **Save the draft to `docs/qa/qa-sheet.md` before running anything**, so the user can see what will be verified. The file starts with a header (`대상 브랜치`, `기준 문서`, `검증 환경`) and ends with a `검증하지 않는 범위` list.
- **If `docs/qa/` already has results:**
  - Same feature: keep the previous results and `수정 이력`, add a new round, and include unresolved `BUG-NNN` files in the re-verification.
  - Different feature: ask the user whether to overwrite or archive the old files (rename) before touching them.

### 2. Map Identifiers from Source (No Screenshots/UI Dumps)
Do NOT dump UI hierarchy or take screenshots to discover elements. Static-grep feature sources instead:
- Visible strings: `Text(`, `label =`, `placeholder =`, `stringResource(`, `strings.xml`.
- Accessibility / Tags: `contentDescription`, `Modifier.testTag`.
- Maestro matches both visible text and `contentDescription`. For dynamic buttons whose description toggles, assert the `contentDescription` directly.
- **No usable identifier (icon-only, duplicated or dynamic text)**: do NOT add a `testTag` yourself. Mark the row `건너뜀` with the reason, and **propose** the change to the user (where to add which tag, with a short diff). Note that Maestro may only read a Compose `testTag` as an `id:` when `testTagsAsResourceId` is enabled on an ancestor, so the proposal may touch two places. Add it only after approval (same gate as Step 8b), following `compose-ui.md`. Text- or position-based selectors are the less invasive alternative.

### 3. Write Ephemeral Flows (/tmp/maestro-flows/)
- Start by emptying `/tmp/maestro-flows/` (it is shared across projects and sessions; stale YAML would be executed too).
- Keep flows there until the Step 8 loop ends, then remove them.
- One flow per scenario group, each independent with its own `launchApp`. Flow order is not guaranteed.
- Assertions: `assertVisible` / `assertNotVisible`. Use `extendedWaitUntil` (timeout: 10000-15000) after network operations.
- After `inputText`, immediately `assertVisible` the typed text to catch input-drop or state-sync bugs before waiting for results.
- **Clearing a prefilled field**: `tapOn` puts the cursor where it was tapped, and `eraseText` only deletes characters before the cursor, so a plain tap can leave trailing characters. Tap the right end of the field first, then erase: `- tapOn: { point: "95%, <field y>%" }`, `- eraseText: <more than the text length>`. Estimate `<field y>` from the layout in source; if that is not possible, one screenshot to read the position is allowed.
- **Unambiguous input assertions**: `assertVisible` matches any element on screen, not only the field. Use an input value that cannot equal any other visible text (add a space or a random token), otherwise the assertion can pass on unrelated content.
- Take screenshots only on persistent failures, never on the happy path.

```yaml
appId: <applicationId>
---
- launchApp:
    clearState: true
- extendedWaitUntil:
    visible: "<text expected after load>"
    timeout: 15000
- tapOn: { point: "95%, <field y>%" }
- eraseText: 30
- inputText: "qa input 1"
- assertVisible: "qa input 1"
- tapOn: "<button text or contentDescription>"
- assertVisible: "<expected result>"
```

### 4. Build, Install, Run
```bash
./gradlew installDebug -q
maestro test /tmp/maestro-flows/ 2>&1 | grep -vE '^\s*$|╭|╰|│' | tail -40
```
- If compilation fails, stop and report the build failure without modifying app code. Leave the planned rows as `건너뜀` with the reason `빌드 실패`.
- Read only pass/fail lines from `maestro test` to save tokens.

### 5. Triage Failures Only (No App Mutation During QA)
`<latest>` is `$(ls -t ~/.maestro/tests | head -1)`. For failed flows only:
1. Identify the failed step: `grep -E 'COMPLETED|FAILED' ~/.maestro/tests/<latest>/maestro.log | tail`.
2. Re-run that single flow alone, up to 3 runs in total (stop early when the outcome is clear):
   - Fails every time → a real failure; continue with item 3.
   - Passes at least once → **Flaky candidate**; go to Step 5a.
   - Filtered logcat shows a crash or exception at the failure time (`adb logcat -d | grep -iE '<applicationId>|FATAL|Exception'`) → treat as a real failure regardless of pass counts.
3. Real failure: inspect at most 1 screenshot from `~/.maestro/tests/<latest>/` and read the relevant source. Decide: **app bug**, **flow bug** (fix the YAML and re-run), or **requirement mismatch**. If you cannot tell, do not guess: record `실패(FAIL)` with confidence "추정" and let the user decide (Step 6, 8b).
4. **No mutation during QA**: do not modify application source code while running or triaging. Only modify YAML flows if the script itself had selector or timing errors, and never weaken or remove an assertion to make a flow pass.

### 5a. Flaky Recovery Plan
Flaky results are never fix targets for app code and never enter the Step 8 fix loop (unless crash evidence turns them into a real failure).
1. **Estimate the cause** (write "미확정" if there is no evidence):

   | Symptom | Likely cause |
   |---|---|
   | Timeout while waiting for initial load, only when flows run back to back | rate-limited API / slow response |
   | Assertion fails right after a transition or animation | timing (not enough waiting) |
   | App start or device response is slow | emulator/device state |

2. **Stabilize** (flows and run method only):
   - Rate limit suspected: leave gaps between flows or run them one at a time instead of one batch, and reduce `clearState` and relaunch counts.
   - Timing suspected: raise the wait/timeout of `extendedWaitUntil`. Do NOT weaken or drop assertions.
   - Device state suspected: restart adb (`adb kill-server && adb start-server`) or reboot the emulator, then re-run.
3. **Re-verify**: run the stabilized flow 3 times in a row. All 3 pass → `성공(PASS)` with the applied measure and evidence in `비고`. Still unstable after 2 stabilization attempts → keep `Flaky(불안정)`, record the cause as "미확정" and a recommendation (e.g. cover it with a unit test or a fake data source), and leave the decision to the user.
4. Never mark a Flaky row `성공(PASS)` without the evidence from step 3.

### 6. Write Bug Documents
Runs for each confirmed app bug, **before** the report, so every `BUG-NNN` referenced in the sheet already exists. Create `docs/qa/bugs/BUG-NNN-<slug>.md` (NNN continues from existing files; Korean). Read the relevant code first to find the cause; cite `file:line`. A suspected bug found in a `단위테스트` row gets a bug document too.

```markdown
# BUG-001: <한 줄 제목>

- 상태: 제안됨 | 승인됨 | 수정됨 | 재검증 통과 | 재검증 실패 | 반려
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

### 7. Fill the QA Sheet & Report (Korean)
- Update `docs/qa/qa-sheet.md` in place with the results (see Result Values). Rows with a bug link their document in `비고` (e.g. `BUG-001`).
- Sections of the sheet:
  1. **검증 요약**: Total, PASS, FAIL, Flaky, 단위테스트 대상, 수동 확인 필요, 건너뜀 (each counted separately).
  2. **QA 테스트 시트 상세**: 7 standard columns filled with results.
  3. **발견된 결함 및 권고사항**: bug list (ID, title, link), Flaky causes/recommendations, recommended unit tests.
  4. **수정 이력**: filled during Step 8 (`라운드 | 결함 ID | 수정 내용 | 재검증 결과`).
  5. **검증하지 않는 범위**.
- If no `실패(FAIL)` caused by an app bug remains: clean up `/tmp/maestro-flows/` and finish.

### 8. Fix Proposal → Approval → QA (Loop)
Runs only when at least one app bug is confirmed.

**8a. Propose, then wait.** Summarize each bug (ID, cause, proposed change, risk) in Korean and ask the user per bug: 승인 / 수정 요청 / 건너뜀. **Do not edit application code before approval.** Writing a new unit test also needs approval; `단위테스트` rows are proposal-only.
- 승인 → bug status `승인됨`, go to 8b.
- 수정 요청 → revise the proposal in the bug document and ask again.
- 건너뜀 → bug status `반려`, the row stays `실패(FAIL)` with the user's decision in `비고`.

**8b. Fix approved bugs only.** Minimal change; read the matching `.agents/conventions/` files first; no unrelated refactors; do not commit unless asked. Set status `수정됨` and add the change to the bug's `수정 이력`.
- If the fix breaks the build, repair it within the same attempt (counts toward the 2-attempt limit). If it cannot be repaired, show the diff and ask the user whether to revert. **Never revert on your own.** Record the rows as `건너뜀` (`빌드 실패`).

**8c. Re-run QA.** Rebuild and install (Step 4) and run **all** flows (regression), not only the failed one. Update the sheet and mark fixed rows `성공(PASS, 수정 후 재검증)`; set the bug status to `재검증 통과`, or `재검증 실패` if it still fails.
- A new failure in a previously passing flow is a **regression**: stop the loop, report the regressions, and propose reverting or adjusting the fix.

**Loop.** Return to Step 5 for remaining or new failures and propose again. Stop when:
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
- Modifying application source code (including adding a `testTag`) during a QA run, or at any time without the user's approval of that specific change.
- Weakening or removing an assertion to make a flow pass.
- Marking a failed or unverified scenario `성공(PASS)`, or marking a Flaky row `성공(PASS)` without the Step 5a re-verification evidence.
- Counting `Flaky(불안정)` inside PASS or FAIL in the summary.
- Treating a device disconnect (infrastructure error) as `실패(FAIL)`.
- An input assertion whose text can also appear elsewhere on screen (false-positive pass).
- Reporting an app bug without a bug document under `docs/qa/bugs/`.
- Running flows before the QA sheet exists in `docs/qa/qa-sheet.md`.
- Reverting changes without asking the user.
- Leaving ephemeral YAML flow files in the repo root or tracking them in git.
- Writing QA documents or the final report in English (must be Korean).

---

## Example

### QA Sheet (`docs/qa/qa-sheet.md`)
```markdown
# 기능 검증 QA 시트: <기능명>

- 대상 브랜치: <브랜치>
- 기준 문서: docs/<analysis>.md
- 검증 환경: <기기>, Maestro <버전>

## 1. 검증 요약
- 총 시나리오: 5건
- 성공(PASS): 2건 (그중 수정 후 재검증 1건)
- 실패(FAIL): 0건
- Flaky(불안정): 1건
- 단위테스트 대상: 1건
- 건너뜀: 1건

## 2. QA 테스트 시트 상세
| ID | 기능/화면 | 테스트 시나리오 | 기대 결과 | 검증 방식 | 결과 | 비고 |
|---|---|---|---|---|---|---|
| TC-001 | 목록 | 앱 실행 | 목록이 로드됨 | UI(Maestro) | 성공(PASS) | |
| TC-002 | 입력 | 입력창에 "qa input 1" 입력 | 입력값이 잘리지 않고 유지됨 | UI(Maestro) | 성공(PASS, 수정 후 재검증) | BUG-001 수정 |
| TC-003 | 새로고침 | 목록을 아래로 당김 | 목록이 다시 표시됨 | UI(Maestro) | Flaky(불안정) | 일괄 실행에서만 실패, 원인 미확정 (3회 중 1회 실패) |
| TC-004 | 디바운스 | 입력 멈춤 후 일정 시간 뒤에만 요청 | 중복 요청 없음 | 단위테스트 | 단위테스트 대상 | 시간 의존 로직 |
| TC-005 | 아이콘 버튼 | 아이콘만 있는 버튼 탭 | 상세 화면으로 이동 | UI(Maestro) | 건너뜀 | 식별자 없음, testTag 추가 제안 중 |

## 3. 발견된 결함 및 권고사항
- [BUG-001](bugs/BUG-001-input-truncated.md): 빠른 입력 시 입력값이 잘림 (TC-002)
- Flaky: TC-003은 연속 실행 시에만 실패. 플로우 간 간격을 두는 안정화 2회 시도 후에도 불안정 → 단위테스트로 대체 권고
- 권고 단위테스트: TC-004

## 4. 수정 이력
| 라운드 | 결함 ID | 수정 내용 | 재검증 결과 |
|---|---|---|---|
| 1 | BUG-001 | 입력 상태를 한 방향으로 전달하도록 변경 | 성공(PASS, 수정 후 재검증) |

## 5. 검증하지 않는 범위
- <검증하지 않는 항목과 이유>
```
