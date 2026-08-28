import { expect, test } from '@playwright/test';
import { enterApp, mockApi, mockAuth, mockProjects, mockStream, sampleRuns } from './mock';

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

test('disables the trigger for a project with no descriptor and explains why', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('relikquary', { exact: true }).click();

	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeDisabled();
	await expect(page.getByText(/no descriptor registered for relikquary/i)).toBeVisible();
});

test('enables the trigger for a registered project', async ({ page }) => {
	await page.goto('/');
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('kontinuance-service', { exact: true }).click();

	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeEnabled();
});

test('disables the trigger when scope changes via the project dropdown, not only via the picker', async ({
	page
}) => {
	// A run that resolves to `relikquary` so the run-derived project select actually offers it as an
	// option — sampleRuns alone only resolve to `kontinuance` (see the other tests in this file).
	const relikquaryRun = {
		id: '#RQ-9001',
		pipeline: 'relikquary',
		status: 'Success',
		repo: 'khorum-oss/relikquary',
		sha: 'deadfeed01',
		startedAt: '2026-07-17T00:00:00Z',
		endedAt: '2026-07-17T00:03:00Z'
	};
	const runs = [...sampleRuns, relikquaryRun];
	await mockApi(page, runs);
	await mockStream(page, runs);

	await page.goto('/');
	await enterApp(page);

	// Enabled first: proves the later disable comes from the scope change, not from being disabled all along.
	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeEnabled();

	await page.getByLabel('filter by project').selectOption('relikquary');

	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeDisabled();
	await expect(page.getByText(/no descriptor registered for relikquary/i)).toBeVisible();
});

test('disables the trigger for a registered-but-not-active project scoped via the dropdown', async ({
	page
}) => {
	// A run that resolves to `infra-charts` so the run-derived project select offers it as an option
	// alongside `kontinuance-service` — sampleRuns alone only resolve to `kontinuance`.
	const infraChartsRun = {
		id: '#IC-5001',
		pipeline: 'infra-charts',
		status: 'Success',
		repo: 'khorum-oss/infra-charts',
		sha: 'feed1234ab',
		startedAt: '2026-07-17T00:00:00Z',
		endedAt: '2026-07-17T00:03:00Z'
	};
	const runs = [...sampleRuns, infraChartsRun];
	await mockApi(page, runs);
	await mockStream(page, runs);

	await page.goto('/');
	await enterApp(page);

	// Enabled first: lands on kontinuance-service, which is both runnable AND the server's active project.
	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeEnabled();

	// infra-charts is registered (runnable) but NOT the active project — switching scope to it must
	// disable the trigger with a reason distinct from "no descriptor registered".
	await page.getByLabel('filter by project').selectOption('infra-charts');

	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeDisabled();
	await expect(page.getByText(/infra-charts is not the active project/i)).toBeVisible();
	await expect(page.getByText(/no descriptor registered/i)).toHaveCount(0);

	// Enabled again: 'all' is the unscoped escape hatch and must not regress to disabled.
	await page.getByLabel('filter by project').selectOption('all');
	await expect(page.getByRole('button', { name: 'RUN PIPELINE' })).toBeEnabled();
});

test('sizes the runs fetch from the window the server derived its counts over', async ({ page }) => {
	// The picker's run counts come from a server-side window; the runs list must load that SAME window, or
	// a count can advertise more runs than the list is able to show. Asserting on the request the browser
	// actually makes is what pins the two together — a hardcoded limit here would silently drift again.
	let runsUrl = '';
	await page.route('**/api/projects', (route) =>
		route.fulfill({
			json: {
				active: 'kontinuance-service',
				runWindow: 7,
				projects: [{ name: 'kontinuance-service', active: true, runnable: true }]
			}
		})
	);
	await page.route('**/api/runs?*', (route) => {
		runsUrl = route.request().url();
		return route.fulfill({ json: { runs: [] } });
	});

	await page.goto('/');
	await enterApp(page);
	await expect.poll(() => runsUrl).toContain('limit=7');
});

test('a scope with zero loaded runs stays selectable after switching to all projects', async ({ page }) => {
	// One-way door: the facet used to offer only run-derived names plus the CURRENT scope, so switching
	// away from a zero-run project dropped it from the list and the only way back was EXIT -> picker.
	await page.goto('/');
	await enterApp(page);
	const facet = page.getByLabel('filter by project');

	await facet.selectOption('relikquary'); // derived, no loaded runs
	await expect(facet).toHaveValue('relikquary');

	await facet.selectOption('all');
	await expect(facet).toHaveValue('all');

	await facet.selectOption('relikquary');
	await expect(facet).toHaveValue('relikquary');
});
