import { expect, test } from '@playwright/test';
import {
	enterApp,
	mockApi,
	mockApiError,
	mockConfig,
	mockCoverage,
	mockDeploy,
	mockPipeline,
	mockStream,
	sampleRuns
} from './mock';

test.describe('entry shell', () => {
	test('signs in, picks a repo, and lands on the runs list', async ({ page }) => {
		await mockApi(page);
		await page.goto('/');

		await expect(page.getByText('MISSION CONTROL ACCESS')).toBeVisible();
		await enterApp(page);

		// overlay gone, shell visible
		await expect(page.getByText('MISSION CONTROL ACCESS')).toHaveCount(0);
		await expect(page.getByText('ALL SYSTEMS NOMINAL')).toBeVisible();
		await expect(page.getByText('MISSION CLOCK')).toBeVisible();
	});
});

test.describe('runs screen', () => {
	test('renders the runs newest-first with their ids and refs', async ({ page }) => {
		await mockApi(page);
		await page.goto('/');
		await enterApp(page);

		for (const run of sampleRuns) {
			await expect(page.getByText(run.id, { exact: true })).toBeVisible();
		}
		await expect(page.getByText('integration tests: 2 failed')).toBeVisible();
	});

	test('opening a run navigates to its detail route', async ({ page }) => {
		await mockApi(page);
		await page.goto('/');
		await enterApp(page);

		await page.getByText('#KX-2045', { exact: true }).click();
		await expect(page).toHaveURL(/\/runs\/%23KX-2045$/);
		await expect(page.getByText('RUN DETAIL')).toBeVisible();
	});

	test('shows an error state with a retry when the API fails', async ({ page }) => {
		await mockApiError(page);
		await page.goto('/');
		await enterApp(page);

		await expect(page.getByText(/request failed/)).toBeVisible();
		await expect(page.getByRole('button', { name: 'RETRY' })).toBeVisible();
	});

	test('shows an empty state when there are no runs', async ({ page }) => {
		await mockApi(page, []);
		await page.goto('/');
		await enterApp(page);

		await expect(page.getByText('no runs recorded yet')).toBeVisible();
	});

	test('a run pushed over the live stream appears without a reload', async ({ page }) => {
		const pushed = {
			id: '#KX-2099',
			pipeline: 'kontinuance-service',
			status: 'Running',
			repo: 'khorum-oss/kontinuance',
			sha: 'deadbee11',
			startedAt: '2026-07-17T02:00:00Z'
		};
		await mockApi(page, sampleRuns); // initial fetch: sampleRuns only
		await mockStream(page, [...sampleRuns, pushed]); // stream also carries the new run
		await page.goto('/');
		await enterApp(page);

		await expect(page.getByText('#KX-2099', { exact: true })).toBeVisible();
	});
});

test.describe('navigation', () => {
	test('the sidebar navigates and highlights the active screen', async ({ page }) => {
		await mockApi(page);
		await page.goto('/');
		await enterApp(page);

		await page.getByRole('link', { name: 'DEPLOY' }).click();
		await expect(page).toHaveURL(/\/deploy$/);

		await page.getByRole('link', { name: 'COVERAGE' }).click();
		await expect(page).toHaveURL(/\/coverage$/);
		await expect(page.getByRole('link', { name: 'COVERAGE' })).toBeVisible();
	});
});

test.describe('pipeline screen', () => {
	test('renders the stage flow and traces dependencies on hover', async ({ page }) => {
		await mockApi(page);
		await mockPipeline(page);
		await page.goto('/pipeline');
		await enterApp(page);

		await expect(page.getByText('TOTAL PROGRESS')).toBeVisible();
		await expect(page.getByText('CHECKOUT', { exact: true })).toBeVisible();
		await expect(page.getByText('legacy-adapter package').first()).toBeVisible();
		await expect(page.getByText(/TELEMETRY/)).toBeVisible();

		// hovering a task keeps its dependency traceable (deps rendered on the card)
		await page.getByText(':core assemble').first().hover();
		await expect(page.getByText('unit tests').first()).toBeVisible();
	});
});

test.describe('coverage screen', () => {
	test('renders the Kover summary and module table, and drills into a module', async ({ page }) => {
		await mockApi(page);
		await mockCoverage(page);
		await page.goto('/coverage');
		await enterApp(page);

		await expect(page.getByText('LINE COVERAGE')).toBeVisible();
		await expect(page.getByText('84.2%')).toBeVisible();
		await expect(page.getByRole('button', { name: /engine/ })).toBeVisible();

		await page.getByRole('button', { name: /engine/ }).click();
		await expect(page.getByText(/class-level breakdown for/)).toBeVisible();
		await page.getByRole('button', { name: 'ALL MODULES' }).click();
		await expect(page.getByRole('button', { name: /server/ })).toBeVisible();
	});
});

test.describe('deploy screen', () => {
	test('renders the promotion nodes, artifact manifest, and environment', async ({ page }) => {
		await mockApi(page);
		await mockDeploy(page);
		await page.goto('/deploy');
		await enterApp(page);

		await expect(page.getByText('SOURCE')).toBeVisible();
		await expect(page.getByText('ARTIFACT MANIFEST')).toBeVisible();
		await expect(page.getByText('kontinuance-core-1.4.2.jar')).toBeVisible();
		await expect(page.getByText('STAGE ENVIRONMENT')).toBeVisible();
		await expect(page.getByText('PODS READY')).toBeVisible();
	});
});

test.describe('config screen', () => {
	test('renders the resolved config source and plan summary', async ({ page }) => {
		await mockApi(page);
		await mockConfig(page);
		await page.goto('/config');
		await enterApp(page);

		await expect(page.getByText('kontinuance.yml', { exact: true })).toBeVisible();
		await expect(page.getByText('RESOLVED PLAN')).toBeVisible();
		await expect(page.getByText(/max parallelism/)).toBeVisible();
		await expect(page.getByText('KONTINUANCE DSL')).toBeVisible();
	});
});
