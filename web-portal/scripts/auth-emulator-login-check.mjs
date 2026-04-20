import process from 'node:process';

const projectId = process.env.FIREBASE_AUTH_EMULATOR_PROJECT || 'roadguard-demo';
const authHost = process.env.AUTH_EMULATOR_HOST || '127.0.0.1:9099';
const apiKey = process.env.FIREBASE_API_KEY || 'demo-api-key';

const operatorEmail = process.env.ROADGUARD_OPERATOR_EMAIL || 'operator@roadguard.it';
const operatorPassword = process.env.ROADGUARD_OPERATOR_PASSWORD || 'RoadGuard!2026';
const operatorAllowlist = new Set(
  (process.env.ROADGUARD_OPERATOR_EMAILS || process.env.VITE_EMULATOR_OPERATOR_EMAILS || operatorEmail)
    .split(',')
    .map((email) => email.trim().toLowerCase())
    .filter((email) => email.length > 0),
);

const idToolkitBase = `http://${authHost}/identitytoolkit.googleapis.com/v1`;
const emulatorAdminBase = `http://${authHost}/emulator/v1/projects/${projectId}`;

const checks = [];

function addCheck(label, passed, details = '') {
  checks.push({ label, passed, details });
}

function decodeJwtPayload(idToken) {
  const parts = idToken.split('.');
  if (parts.length < 2) {
    throw new Error('Invalid ID token payload format.');
  }
  const decoded = Buffer.from(parts[1], 'base64url').toString('utf8');
  return JSON.parse(decoded);
}

async function requestJson(url, method = 'POST', body = undefined, expectOk = true) {
  const response = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : {};

  if (expectOk && !response.ok) {
    throw new Error(`Request failed (${response.status}): ${JSON.stringify(payload)}`);
  }

  return { ok: response.ok, status: response.status, payload };
}

async function resetAuthEmulatorAccounts() {
  await requestJson(`${emulatorAdminBase}/accounts`, 'DELETE', undefined, true);
}

async function run() {
  await resetAuthEmulatorAccounts();

  const signUp = await requestJson(
    `${idToolkitBase}/accounts:signUp?key=${apiKey}`,
    'POST',
    {
      email: operatorEmail,
      password: operatorPassword,
      returnSecureToken: true,
    },
    true,
  );

  const validLogin = await requestJson(
    `${idToolkitBase}/accounts:signInWithPassword?key=${apiKey}`,
    'POST',
    {
      email: operatorEmail,
      password: operatorPassword,
      returnSecureToken: true,
    },
    true,
  );

  const hasIdToken = typeof validLogin.payload.idToken === 'string' && validLogin.payload.idToken.length > 0;
  addCheck('valid login returns ID token', hasIdToken, hasIdToken ? '' : JSON.stringify(validLogin.payload));

  let tokenEmail = '';
  if (hasIdToken) {
    const tokenPayload = decodeJwtPayload(validLogin.payload.idToken);
    tokenEmail = (tokenPayload.email || '').toLowerCase();
  }

  const protectedAccessAllowed = tokenEmail !== '' && operatorAllowlist.has(tokenEmail);
  addCheck(
    'protected access identity is in operator allowlist',
    protectedAccessAllowed,
    protectedAccessAllowed ? '' : `email=${tokenEmail || 'missing'}`,
  );

  const invalidLogin = await requestJson(
    `${idToolkitBase}/accounts:signInWithPassword?key=${apiKey}`,
    'POST',
    {
      email: operatorEmail,
      password: `${operatorPassword}-wrong`,
      returnSecureToken: true,
    },
    false,
  );

  const invalidMessage = invalidLogin.payload?.error?.message || '';
  const invalidRejected =
    !invalidLogin.ok && (invalidMessage.includes('INVALID_LOGIN_CREDENTIALS') || invalidMessage.includes('INVALID_PASSWORD'));

  addCheck(
    'invalid login is rejected',
    invalidRejected,
    invalidRejected ? '' : JSON.stringify(invalidLogin.payload),
  );

  console.log('Auth Emulator Login Checks');
  for (const check of checks) {
    console.log(`- ${check.passed ? 'PASS' : 'FAIL'}: ${check.label}`);
    if (!check.passed && check.details) {
      console.log(`  details: ${check.details}`);
    }
  }

  const failures = checks.filter((item) => !item.passed).length;
  console.log(`Summary: ${checks.length - failures}/${checks.length} checks passed`);

  if (failures > 0) {
    process.exit(1);
  }
}

run().catch((error) => {
  console.error('Auth emulator check failed:', error);
  process.exit(1);
});
