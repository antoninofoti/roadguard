# RoadGuard Web Portal

Operator dashboard for RoadGuard report triage and status workflows.

## Authentication Strategy

Final rollout uses a single mode: **Auth Emulator**.

- `VITE_AUTH_MODE=emulator` is mandatory.
- `Auth Emulator` runs on `127.0.0.1:9099`.
- `Firestore Emulator` runs on `127.0.0.1:8080`.

## Local Setup

### Prerequisites

- Firebase emulators running (Auth + Firestore)
- Node.js 18+

### Step 1: Configure Environment

Copy the environment template and customize if needed:

```bash
cp .env.example .env.local
```

The `.env.local` file should contain:

- `VITE_AUTH_MODE=emulator`
- `VITE_AUTH_EMULATOR_HOST=127.0.0.1`
- `VITE_AUTH_EMULATOR_PORT=9099`
- `VITE_FIRESTORE_EMULATOR_HOST=127.0.0.1`
- `VITE_FIRESTORE_EMULATOR_PORT=8080`
- `VITE_EMULATOR_OPERATOR_EMAILS=operator.release@roadguard.local,operator@roadguard.it`

### Step 2: Install Dependencies

```bash
npm ci
```

### Step 3: Start Firebase Emulators (in a separate terminal)

```bash
npx firebase-tools@15.16.0 emulators:start --only auth,firestore --project roadguard-demo
```

The emulators will start on:

- **Auth Emulator:** `http://127.0.0.1:9099` (internal)
- **Firestore Emulator:** `http://127.0.0.1:8080` (internal)
- **Emulator UI:** `http://127.0.0.1:4000/` (browser-accessible)

### Step 4: Provision Test Operator Account

In the web-portal directory, run:

```bash
node scripts/auth-emulator-login-check.mjs
```

This script creates/validates the operator test account.

### Step 5: Start Development Server

```bash
npm run dev
```

The portal runs on `http://127.0.0.1:4501/`.
Port `4500` is reserved by the Firebase emulator stack in this workspace.

### Step 6: Test Login

Open `http://127.0.0.1:4501/` in your browser and use the test credentials below.

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

## Test Credentials (Emulator Mode)

The following test accounts are provisioned automatically by the Auth Emulator and the setup scripts:

### Operator (Role: Operator)

- **Email:** `operator.release@roadguard.local`
- **Password:** `RoadGuard!2026`
- **Access:** Full operator and administrator dashboard access

### Admin (Role: Admin)

- **Email:** `admin@example.com`
- **Password:** `Password123!`
- **Access:** Full administrative capabilities

### User (Role: User)

- **Email:** `user@example.com`
- **Password:** `Password123!`
- **Access:** Denied (this portal is operator/admin only)

**Notes:**

- These are test accounts only and are managed by the Auth Emulator.
- Protected access in emulator mode uses the `VITE_EMULATOR_OPERATOR_EMAILS` allowlist in `.env.local`.
- The operator account is validated by the `scripts/auth-emulator-login-check.mjs` provisioning script.
