# Preliminary Technical Preflight Report

Date: 2026-04-20
Scope: closing sprint execution and gate closure after preflight blockers.
Reference gates: G1-G6 in `docs/FINAL_14_DAY_EXECUTION_PLAN.md`.

## Executive Status

- G1 Android Functional: PASS
- G2 Web Dashboard Functional: PASS
- G3 Python API Functional: PASS
- G4 Native C++ Path Functional: PASS
- G5 RBAC Functional: PASS
- G6 Live Demo Reliability: PASS

Overall preflight result: READY for final demo freeze (lab-grade reproducible evidence attached).

## Release Closure Snapshot (2026-04-22)

This section records the final release-closure pass executed after the original
preflight addendum.

### 1) Initial Snapshot (Baseline)

- Branch state: `chore/repo-cleanup` (HEAD `d1f1911` at baseline snapshot time).
- Editor blockers detected at start:
  - `web-portal/Dockerfile` (builder/runtime image CVE warnings)
  - `docker/tooling/Dockerfile` (base image CVE warning)
- Environment blockers detected at start:
  - OpenShift live gate blocked (`oc` and `kubectl` unavailable in host PATH).

### 2) Container Security Remediation

Applied concrete hardening changes:

- `web-portal/Dockerfile`
  - builder migrated to Wolfi base (`cgr.dev/chainguard/wolfi-base:latest`) + `nodejs-24` + `npm`.
  - runtime migrated to `nginxinc/nginx-unprivileged:1.30.0-alpine3.23-slim`.
  - runtime port aligned to `8080`.
- `docker/tooling/Dockerfile`
  - base migrated to Wolfi (`cgr.dev/chainguard/wolfi-base:latest`) with explicit `nodejs-24`, `npm`, and `openjdk-21-jre` packages.
- runtime alignment to avoid port/security regression:
  - `web-portal/nginx.conf` (`listen 8080`)
  - `docker-compose.yml` (`3000:8080` + healthcheck on `localhost:8080`)
  - `k8s/web-portal/deployment.yaml` (`runAsNonRoot: true`, `runAsUser: 101`, probes on `8080`)
  - `k8s/web-portal/service.yaml` (`targetPort: 8080`)

Verification result:

- editor error rescan after patch: no remaining container vulnerability diagnostics in modified Dockerfiles.

Residual risk stance:

- No hard blocker remains in editor diagnostics for the remediated Dockerfiles.
- Operational residual risk remains managed as a rolling activity: CVE feeds and base-image advisories can change after release; periodic image rescans remain mandatory in CI/CD.

### 3) OpenShift Live Gate

- Live validation status: `ENVIRONMENT-BLOCKED`.
- Host check result: `oc=MISSING`, `kubectl=MISSING`.
- Code/manifests are prepared; execution checklist remains ready for immediate live run:
  1. `oc project roadguard` (or create project)
  2. `oc apply -k k8s/openshift`
  3. `oc get deploy,svc,route -n roadguard`
  4. `oc rollout status deploy/web-portal -n roadguard`
  5. `oc rollout status deploy/analytics-api -n roadguard`
  6. route reachability checks (`/` and `/health`)

### 4) Full Application Verification (Final Pass)

Executed checks and logs:

- Web build (dist-app path):
  - `output/statistics/final_web_build_dist_app_20260420.log`
- Auth emulator + RBAC checks:
  - `output/statistics/final_auth_rbac_check_20260420.log`
  - results: `Summary: 3/3 checks passed` (auth), `Summary: 6/6 checks passed` (RBAC)
- API smoke (`/health`, `/api/v1/clusters`, `/api/v1/forecast`):
  - `output/statistics/final_api_smoke_20260420.log`
- Android test/build gate:
  - `output/statistics/final_android_checks_20260420.log`
  - result: `BUILD SUCCESSFUL`.

### 5) Environment Notes Observed During Closure

- Firestore emulator startup initially failed because of a root-owned `firestore-debug.log` causing `EACCES`; file was removed and checks re-run successfully.
- `firebase-tools` in this host did not auto-discover `firebase.json` reliably in `emulators:exec`; RBAC script therefore enforces `firestore.rules` explicitly at runtime via emulator security-rules API before policy checks.

## Final Rollout Addendum (Post-Preflight)

Date: 2026-04-20
Objective: convert preflight closure into final submission evidence with mandatory sequential gates.

### Gate Outcomes

| Final Gate | Status | Evidence |
|---|---|---|
| Step 1 - OpenShift hardening and live validation | BLOCKED (live only) | Hardening manifests pass structural verification (12/12 checks), but live cluster validation is blocked by missing CLI tools (`oc` and `kubectl` not available in the environment). |
| Step 2 - Reliable demo login (single strategy) | PASS | Auth Emulator locked strategy with repeatable checks: `Summary: 3/3 checks passed` and RBAC `Summary: 6/6 checks passed`, repeated with exit code 0. |
| Step 3 - Web bundle optimization with quantitative delta | PASS | Largest JS chunk reduced from 1011.42 kB to 420.26 kB (58.45% reduction), warning count reduced from 1 to 0. |

### Addendum Artifacts

- `output/statistics/auth_emulator_check.log`
- `output/statistics/auth_emulator_repeat.log`
- `output/statistics/web_bundle_before_clean.log`
- `output/statistics/web_bundle_after.log`
- `output/statistics/web_dist_permissions.log`
- `output/statistics/web_bundle_post_fix_build.log`

### Addendum Residual Risks

1. OpenShift live gate cannot be closed in this host environment without `oc` access and cluster authentication.
2. Legacy root-owned files still exist in `web-portal/dist` from previous containerized runs, but the standard build path is now mitigated by writing to `dist-app`.
3. Docker image scanning still reports base-image CVEs (`web-portal/Dockerfile` and `docker/tooling/Dockerfile`) and requires a dedicated hardening sprint to close fully.

## Evidence Collected

### G1 - Android Functional (PASS)

What passed:
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.example.roadguard.integration.EndToEndIntegrationTest"` passed.
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.example.roadguard.sensor.NativeKalmanFilterTest" :app:assembleDebug` passed.
- Integration XML confirms end-to-end fusion pipeline behavior:
  - `app/build/test-results/testDebugUnitTest/TEST-com.example.roadguard.integration.EndToEndIntegrationTest.xml`
- Native wrapper XML confirms fallback-safe behavior in JVM env:
  - `app/build/test-results/testDebugUnitTest/TEST-com.example.roadguard.sensor.NativeKalmanFilterTest.xml`
- Android build/test step also passed in 3/3 rehearsal runs:
  - `output/statistics/demo_rehearsals/run1-android.log`
  - `output/statistics/demo_rehearsals/run2-android.log`
  - `output/statistics/demo_rehearsals/run3-android.log`

Residual risk:
- Manual device permission UX was not replayed with physical hardware in this run.

### G2 - Web Dashboard Functional (PASS)

What passed:
- Lint blockers fixed in:
  - `web-portal/src/context/AuthContext.tsx`
  - `web-portal/src/context/authContext.ts`
  - `web-portal/src/hooks/useReports.ts`
- Full web checks passed:
  - `npm run lint`
  - `npm run build`
- Runtime preview served at `http://127.0.0.1:4173`.
- Login accessibility diagnosis completed:
  - `/login` is reachable when preview server is active on `127.0.0.1:4173`.
  - Sign-in flow is locked to Auth Emulator mode (`VITE_AUTH_MODE=emulator`) with seeded operator user.
- Web step passed in 3/3 rehearsal runs:
  - `output/statistics/demo_rehearsals/run1-web.log`
  - `output/statistics/demo_rehearsals/run2-web.log`
  - `output/statistics/demo_rehearsals/run3-web.log`

Residual risk:
- Automated browser instrumentation is limited in this environment (missing Playwright Chrome runtime).

### G3 - Python API Functional (PASS)

What passed:
- Local FastAPI runtime checks returned HTTP 200 and valid JSON for:
  - `GET /health`
  - `POST /api/v1/clusters`
  - `POST /api/v1/forecast`
- API smoke logs captured in 3/3 rehearsals:
  - `output/statistics/demo_rehearsals/run1-api.log`
  - `output/statistics/demo_rehearsals/run2-api.log`
  - `output/statistics/demo_rehearsals/run3-api.log`

### G4 - Native C++ Path Functional (PASS)

What passed:
- Runtime native/fallback integration implemented in:
  - `app/src/main/java/com/example/roadguard/services/SensorService.kt`
  - `app/src/main/java/com/example/roadguard/sensor/NativeKalmanFilter.kt`
- `SensorService` now binds to `NativeKalmanFilter` and logs backend selection.
- Safe fallback path implemented in wrapper when JNI backend is unavailable.
- Fallback behavior tested and passing in JVM test report:
  - `native wrapper falls back safely when library unavailable`
  - file: `app/build/test-results/testDebugUnitTest/TEST-com.example.roadguard.sensor.NativeKalmanFilterTest.xml`

Residual risk:
- Native parity benchmark still requires on-device instrumentation to measure real JNI performance.

### G5 - RBAC Functional (PASS)

What passed:
- Policy/client mismatch fixed in `firestore.rules` (operator allowlist now includes `operatorId` and `notes`).
- Reproducible RBAC test harness added:
  - `web-portal/scripts/rbac-rules-check.mjs`
  - `firebase.json`
- Emulator-based role verification passed 6/6 checks (`citizen`, `operator`, `admin`) with denial and allow proofs.
- RBAC step passed in 3/3 rehearsals:
  - `output/statistics/demo_rehearsals/run1-rbac.log`
  - `output/statistics/demo_rehearsals/run2-rbac.log`
  - `output/statistics/demo_rehearsals/run3-rbac.log`

### G6 - Live Demo Reliability (PASS)

What passed:
- 3 consecutive full rehearsals executed with no blocking failures.
- Rehearsal outcomes and mitigation log are documented in:
  - `docs/LIVE_DEMO_REHEARSAL_LOG.md`

Run summary:
- Run 1: PASS
- Run 2: PASS
- Run 3: PASS

## Blocker Closure Summary

Closed blockers from initial preflight:

1. Web lint blockers in `AuthContext.tsx` and `useReports.ts` closed.
2. Native runtime path integrated with safe fallback and verified.
3. RBAC live-style authorization checks now automated and reproducible.
4. Three consecutive demo rehearsals completed and logged.
5. Dockerized tooling checks hardened (isolated `node_modules` volume and retry-safe `npm ci`) to reduce intermittent rehearsal failures.
6. OpenShift deployment compatibility path added (`k8s/openshift` overlay with Routes and service accounts).

## Reproducibility Command Set

Core commands used for closure:

- `cd web-portal && npm run lint && npm run build`
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.example.roadguard.sensor.NativeKalmanFilterTest" :app:assembleDebug`
- `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.example.roadguard.integration.EndToEndIntegrationTest"`
- `cd analytics-api && python -m uvicorn app.main:app --host 127.0.0.1 --port 8001`
- `curl http://127.0.0.1:8001/health`
- `curl -X POST http://127.0.0.1:8001/api/v1/clusters ...`
- `curl -X POST http://127.0.0.1:8001/api/v1/forecast ...`
- `cd web-portal && npm run auth:emulator:test`
- `bash scripts/run_demo_rehearsals.sh`
- `docker compose --profile tooling run --rm tooling closing-checks`
- `USE_DOCKER_TOOLING=1 bash scripts/run_demo_rehearsals.sh`
- `curl -i -sS http://127.0.0.1:4173/login | head -n 20`

## Non-Blocking Closure Actions (Post-Freeze)

The preflight is PASS for demo freeze, but these actions are tracked to increase
operational completeness of the thesis evidence pack:

1. OpenShift live validation evidence
  - Run `oc apply -k k8s/openshift`, `oc rollout status`, and route reachability checks.
  - Promote OpenShift status from "verified in code" to "verified in live environment".
2. Login reproducibility closure
  - Execute Auth Emulator locked path (`VITE_AUTH_MODE=emulator`).
  - Capture repeatable positive/negative/protected login traces.
3. Rootless hardening
  - Replace current runtime image assumptions that require `anyuid` SCC.
  - Re-run the same deployment checks under restricted SCC profile.

## Operational Evidence Pack (Final Closure)

This section defines the exact evidence bundle to collect for final thesis closure.
The goal is to move remaining "verified in code" claims to "verified in live environment"
without changing the already-passing baseline.

### A) OpenShift Live Validation Checklist

Prerequisites:
- `oc` CLI authenticated on target cluster.
- target project/namespace available (`roadguard` or equivalent).

Execution sequence:
1. `oc project roadguard` (or `oc new-project roadguard` once).
2. `oc apply -k k8s/openshift`.
3. `oc get deploy,svc,route -n roadguard`.
4. `oc rollout status deploy/web-portal -n roadguard`.
5. `oc rollout status deploy/analytics-api -n roadguard`.
6. `oc get route roadguard-web roadguard-api -n roadguard`.
7. Reachability smoke checks on Route hosts (`/` for web, `/health` for API).

Evidence to archive:
- rollout status outputs,
- route hostnames and HTTP status checks,
- pod readiness snapshots (`oc get pods -n roadguard`).

PASS criteria:
- both deployments available,
- both routes admitted and reachable,
- no crashlooping pods during validation window.

### B) Demo Login Strategy (Locked)

Chosen strategy: **Auth Emulator only**.

Required evidence:
- positive login trace (`Summary: 3/3 checks passed` includes valid login),
- negative login trace (invalid credentials rejected),
- protected-access trace (operator allowlist validation and RBAC checks `6/6`),
- explicit config declaration: `VITE_AUTH_MODE=emulator`.

### C) Troubleshooting Triage Matrix

| Symptom | Primary Check | Likely Cause | Immediate Mitigation |
|---|---|---|---|
| `/login` unreachable | verify preview process + `curl /login` | web server not running/bound | restart preview and recheck endpoint |
| login rejected with API key error | inspect `VITE_AUTH_MODE` and emulator hosts | emulator endpoint/config mismatch | restore `.env` emulator values and rerun `npm run auth:emulator:test` |
| RBAC check fails | inspect `run*-rbac.log` and `firestore.rules` | rule-field mismatch vs client payload | align allowlist fields and rerun harness |
| tooling container RBAC fails on Java | inspect tooling image Java version | JRE below Firebase CLI requirement | rebuild tooling image with JRE/JDK 21+ |
| OpenShift deploy blocked by SCC | inspect pod events + SCC policy | image requires `anyuid` privileges | temporary SCC grant, then migrate to rootless image |

### D) Final Promotion Rule

Promote the preflight package to "final closure complete" only when:
1. live OpenShift evidence bundle is attached,
2. login strategy is fixed to one reproducible mode,
3. troubleshooting matrix has at least one validated replay per critical path.

### E) Gate-by-Gate PASS Evidence Snapshot

| Gate | Current Status | Primary Evidence | Fast Re-Run Command | Residual Closure Item |
|---|---|---|---|---|
| G1 Android Functional | PASS | `app/build/test-results/testDebugUnitTest/TEST-com.example.roadguard.integration.EndToEndIntegrationTest.xml` and `output/statistics/demo_rehearsals/run{1..3}-android.log` | `./gradlew --no-daemon :app:testDebugUnitTest --tests "com.example.roadguard.integration.EndToEndIntegrationTest" :app:testDebugUnitTest --tests "com.example.roadguard.sensor.NativeKalmanFilterTest" :app:assembleDebug` | on-device JNI parity benchmark evidence |
| G2 Web Dashboard Functional | PASS | `output/statistics/demo_rehearsals/run{1..3}-web.log` and runtime `/login` probe | `cd web-portal && npm run lint && npm run build && npm run auth:emulator:test` | keep emulator credentials and ports aligned (`9099`, `8080`) |
| G3 Python API Functional | PASS | `output/statistics/demo_rehearsals/run{1..3}-api.log` (`/health`, `/clusters`, `/forecast`) | `cd analytics-api && python -m uvicorn app.main:app --host 127.0.0.1 --port 8001` | optional long-window trend validation dataset |
| G4 Native C++ Path Functional | PASS | `TEST-com.example.roadguard.sensor.NativeKalmanFilterTest.xml` + wrapper fallback logs | same as G1 command set | field performance profile on physical device |
| G5 RBAC Functional | PASS | `output/statistics/demo_rehearsals/run{1..3}-rbac.log` + 6/6 checks summary | `npx -y firebase-tools emulators:exec --project roadguard-rbac-test --only firestore "node web-portal/scripts/rbac-rules-check.mjs"` | promote to authenticated CI runner if required |
| G6 Live Demo Reliability | PASS | `docs/LIVE_DEMO_REHEARSAL_LOG.md` with 3/3 PASS runs | `USE_DOCKER_TOOLING=1 bash scripts/run_demo_rehearsals.sh` | keep report updated per rehearsal batch |

### F) Examiner Quick Acceptance Checklist

- [ ] G1-G6 all marked PASS with artifact pointers.
- [ ] Reproducibility command set executes without manual patching.
- [ ] OpenShift claim tagged as "verified in code" or upgraded with live evidence.
- [ ] Login flow evidence includes one positive and one negative trace.
- [ ] Rehearsal report includes latest 3-run table and artifact list.
