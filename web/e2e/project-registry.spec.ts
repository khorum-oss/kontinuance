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

	// a derived project has no registered descriptor, so setting a source would 404 — don't offer it
	await expect(row.getByText('SET SOURCE')).toHaveCount(0);
	await expect(row.getByText('EDIT SOURCE')).toHaveCount(0);
	// a registered project keeps its source control
	await expect(registeredRow.getByText('SET SOURCE')).toBeVisible();
});

test('selecting a project scopes the runs list and all projects restores it', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('relikquary', { exact: true }).click();

	// sampleRuns belong to `kontinuance`, so scoping to `relikquary` empties the list honestly.
	await expect(page.getByText(/no runs match/i)).toBeVisible();

	await page.getByRole('combobox').filter({ hasText: 'ALL PROJECTS' }).selectOption('all');
	await expect(page.getByText('#KX-2046')).toBeVisible();
});

test('the project select names its scope even when that scope has zero matching runs', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('relikquary', { exact: true }).click();

	// relikquary matches zero loaded runs (sampleRuns resolve to `kontinuance`), so its option would be
	// absent from the run-derived list unless the active scope is unioned in — without that, the browser
	// renders the <select> blank instead of naming the filter actually being applied.
	await expect(page.getByLabel('filter by project')).toHaveValue('relikquary');
});
