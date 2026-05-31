import { test, expect } from '@playwright/test';

test('Settings: test connections and save', async ({ page }) => {
  // Stub GET /api/settings and capture PATCH to /api/settings
  let patched: any = null;
  await page.route('**/api/settings', async (route) => {
    const req = route.request();
    if (req.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          paperlessBaseUrl: '',
          paperlessApiTokenSet: false,
          openRouterModel: '',
          openRouterApiKeySet: false,
          syncIntervalMinutes: null,
        }),
      });
      return;
    }
    if (req.method() === 'PATCH') {
      const body = await req.postData();
      try {
        patched = body ? JSON.parse(body) : {};
      } catch (e) {
        patched = body;
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(patched) });
      return;
    }
    await route.continue();
  });

  // Stub test endpoints
  await page.route('**/api/settings/test/paperless', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ok: true }) });
  });
  await page.route('**/api/settings/test/openrouter', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ok: true }) });
  });


  // Go to settings page
  await page.goto('/settings');

  // Wait for initial settings GET to complete so the form is stable
  await page.waitForResponse((resp) => resp.url().includes('/api/settings') && resp.status() === 200);

  // Fill Paperless settings and test
  await page.getByLabel('Base URL').fill('http://paperless.local');
  await page.getByLabel('API Token').fill('sekret-token');
  // Click the first "Test Connection" button (Paperless) and assert its nearby OK indicator
  const paperlessBtn = page.getByRole('button', { name: 'Test Connection' }).first();
  await paperlessBtn.click();
  await expect(paperlessBtn.locator('..').getByText('OK', { exact: true })).toBeVisible();

  // Fill OpenRouter settings and test
  await page.getByLabel('API Key').fill('open-key');
  await page.getByLabel('Model').fill('gpt-4o-mini');
  // Click the second "Test Connection" button (OpenRouter) and assert its nearby OK indicator
  const openBtn = page.getByRole('button', { name: 'Test Connection' }).nth(1);
  await openBtn.click();
  await expect(openBtn.locator('..').getByText('OK', { exact: true })).toBeVisible();

  // Save settings and accept confirmation dialog
  page.on('dialog', (dialog) => dialog.accept());
  // Verify input values are set before saving
  await expect(await page.getByLabel('Base URL').inputValue()).toBe('http://paperless.local');
  await expect(await page.getByLabel('API Token').inputValue()).toBe('sekret-token');
  await expect(await page.getByLabel('API Key').inputValue()).toBe('open-key');
  await expect(await page.getByLabel('Model').inputValue()).toBe('gpt-4o-mini');

  await page.getByRole('button', { name: 'Save Settings' }).click();
  // The app shows an alert after saving; wait for it (ensures PATCH completed)
  await page.waitForEvent('dialog');
  // Verify PATCH payload was sent
  expect(patched).not.toBeNull();
  expect(patched.paperlessBaseUrl).toBe('http://paperless.local');
  expect(patched.paperlessApiToken).toBe('sekret-token');
  expect(patched.openRouterApiKey).toBe('open-key');
  expect(patched.openRouterModel).toBe('gpt-4o-mini');
});
