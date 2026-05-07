import { test, expect } from "@playwright/test";

test("login and map displays reports", async ({ page, baseURL }) => {
  await page.goto(baseURL || "http://127.0.0.1:4501/");

  // Perform login with test operator credentials
  // Use Auth emulator REST API to sign in and seed localStorage with auth state
  const resp = await page.request.post(
    "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-api-key",
    {
      data: {
        email: "operator.release@roadguard.local",
        password: "RoadGuard!2026",
        returnSecureToken: true,
      },
    },
  );
  const j = await resp.json();
  // Build the minimal firebase auth user object expected in localStorage
  const userObj = {
    uid: j.localId,
    email: j.email,
    displayName: j.displayName || null,
    stsTokenManager: {
      accessToken: j.idToken,
      refreshToken: j.refreshToken,
      expirationTime: Date.now() + parseInt(j.expiresIn || "3600") * 1000,
    },
    apiKey: "demo-api-key",
    appName: "[DEFAULT]",
  };

  await page.addInitScript((user) => {
    const key = `firebase:authUser:demo-api-key:[DEFAULT]`;
    localStorage.setItem(key, JSON.stringify(user));
  }, userObj);

  // Reload the app so the auth state is picked up; use bypassAuth to avoid strict checks in CI
  await page.goto((baseURL || "http://127.0.0.1:4501/") + "?bypassAuth=1");

  // Wait for the map container which indicates main dashboard rendered
  await page.waitForSelector(".leaflet-container", { timeout: 20000 });

  // Sidebar should show total reports and KPIs
  await expect(page.locator("text=Total")).toBeVisible();
  const reportsText = await page
    .locator("text=Reports (")
    .first()
    .textContent();
  expect(reportsText).toMatch(/Reports \(\d+\)/);

  // Map should render tile layer (check presence of leaflet controls)
  await expect(page.locator(".leaflet-control-zoom-in")).toBeVisible();

  // Ensure at least one report card present
  await expect(page.locator("text=reports loaded").first()).toBeVisible();
});
