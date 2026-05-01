# RoadGuard Web Portal

Operator dashboard for RoadGuard report triage and status workflows.

## Authentication Strategy

Final rollout uses a single mode: **Auth Emulator**.

- `VITE_AUTH_MODE=emulator` is mandatory.
- `Auth Emulator` runs on `127.0.0.1:9099`.
- `Firestore Emulator` runs on `127.0.0.1:8080`.

## Local Setup

1. Copy env template:

```bash
cp .env.example .env
```

2. Install dependencies:

```bash
npm ci
```

3. Start development server:

```bash
npm run dev
```

The portal runs on `http://127.0.0.1:4501/`.
Port `4500` is reserved by the Firebase emulator stack in this workspace.

## Validation Commands

- Lint and build:

```bash
npm run lint
npm run build
```

- Emulator login + RBAC smoke suite (valid login, invalid login, protected access evidence):

```bash
npm run auth:emulator:test
```

- Repeatability run (consecutive executions):

```bash
npm run auth:emulator:test:repeat
```

## Demo Operator Credentials (Emulator)

- Email: `operator@roadguard.it`
- Password: `RoadGuard!2026`

Protected access in emulator mode uses the `VITE_EMULATOR_OPERATOR_EMAILS` allowlist.
