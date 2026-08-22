import { expect, test } from '@playwright/test';
import { mockApi, mockAuth, mockProjects, mockStream } from './mock';

test.beforeEach(async ({ page }) => {
	await mockAuth(page);
	await mockProjects(page);
	await mockApi(page);
	await mockStream(page);
});

test('badges a derived project and shows its run count in the picker', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();

	const row = page.locator('.repo', { hasText: 'relikquary' });
	await expect(row.getByText('DERIVED')).toBeVisible();
	await expect(row.getByText(/12 runs/)).toBeVisible();

	// a derived project has no registered descriptor, so it cannot be run — AVAILABLE would be a lie
	await expect(row.getByText('AVAILABLE')).toHaveCount(0);
	// a registered, non-active project is still selectable and must still say so
	const registeredRow = page.locator('.repo', { hasText: 'infra-charts' });
	await expect(registeredRow.getByText('AVAILABLE')).toBeVisible();
});
