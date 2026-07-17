import type { Page } from '@playwright/test';

// Wire-shape run records the UI's client understands. Kept here (not imported from src) so the E2E
// suite exercises the real network boundary with realistic payloads.
export const sampleRuns = [
	{
		id: '#KX-2046',
		pipeline: 'kontinuance-service',
		status: 'Running',
		repo: 'khorum-oss/kontinuance',
		sha: 'a3f19c2ff',
		startedAt: '2026-07-17T00:00:00Z'
	},
	{
		id: '#KX-2045',
		pipeline: 'kontinuance-service',
		status: 'Success',
		repo: 'khorum-oss/kontinuance',
		sha: '9b02d1e00',
		startedAt: '2026-07-17T00:00:00Z',
		endedAt: '2026-07-17T00:05:01Z'
	},
	{
		id: '#KX-2044',
		pipeline: 'kontinuance-service',
		status: 'Failed',
		failingStep: 'integration tests',
		reason: '2 failed',
		repo: 'khorum-oss/kontinuance',
		sha: '77aa310aa',
		startedAt: '2026-07-17T00:00:00Z',
		endedAt: '2026-07-17T00:02:47Z'
	}
];

const listUrl = /\/api\/runs(\?.*)?$/;
const detailUrl = /\/api\/runs\/[^/?]+$/;

/** Serve the runs list + run-by-id from an in-memory fixture set. */
export async function mockApi(page: Page, runs = sampleRuns): Promise<void> {
	await page.route(listUrl, (route) => route.fulfill({ json: { runs } }));
	await page.route(detailUrl, (route) => {
		const id = decodeURIComponent(new URL(route.request().url()).pathname.split('/').pop() ?? '');
		const run = runs.find((r) => r.id === id);
		return run
			? route.fulfill({ json: run })
			: route.fulfill({ status: 404, json: { error: 'not found' } });
	});
}

/** Make every runs request fail, to exercise the error state. */
export async function mockApiError(page: Page): Promise<void> {
	await page.route(/\/api\/runs/, (route) =>
		route.fulfill({ status: 502, json: { error: 'bad gateway' } })
	);
}

/** Drive the presentational entry flow (sign in → repo pick → enter). */
export async function enterApp(page: Page): Promise<void> {
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByText('SIGN IN').click();
	await page.getByText('ENTER MISSION CONTROL').click();
}
