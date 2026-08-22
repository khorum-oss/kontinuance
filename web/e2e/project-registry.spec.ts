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
});
