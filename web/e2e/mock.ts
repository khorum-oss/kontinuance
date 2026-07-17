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
const streamUrl = /\/api\/runs\/stream/;

function sseBody(runs: typeof sampleRuns): string {
	return runs.map((r) => `event: run\ndata: ${JSON.stringify(r)}\n\n`).join('');
}

/** Mock the SSE stream to emit `runs` as `run` events. */
export async function mockStream(page: Page, runs = sampleRuns): Promise<void> {
	await page.route(streamUrl, (route) =>
		route.fulfill({ contentType: 'text/event-stream', body: sseBody(runs) })
	);
}

const triggerUrl = /\/api\/runs\/trigger$/;

/** The run a trigger starts; folded into the list once POST /api/runs/trigger has been called. */
const startedRun = {
	id: '#KX-2100',
	pipeline: 'kontinuance-service',
	status: 'Running',
	repo: 'khorum-oss/kontinuance',
	sha: 'deadbeef1a',
	startedAt: '2026-07-17T00:10:00Z'
};

/** Serve the runs list + run-by-id + live stream from an in-memory fixture set. */
export async function mockApi(page: Page, runs = sampleRuns): Promise<void> {
	let triggered = false;
	await page.route(listUrl, (route) =>
		route.fulfill({ json: { runs: triggered ? [startedRun, ...runs] : runs } })
	);
	await page.route(detailUrl, (route) => {
		const id = decodeURIComponent(new URL(route.request().url()).pathname.split('/').pop() ?? '');
		const run = runs.find((r) => r.id === id);
		return run
			? route.fulfill({ json: run })
			: route.fulfill({ status: 404, json: { error: 'not found' } });
	});
	await mockStream(page, runs);
	// Registered after detailUrl (which also matches /api/runs/trigger) so this wins for the POST.
	await page.route(triggerUrl, (route) => {
		triggered = true;
		return route.fulfill({ status: 202, json: { runId: startedRun.id } });
	});
}

/** Serve a single run paused at a manual-approval gate; approving flips it to Success. */
export async function mockWaitingRun(page: Page, id = 'run-approve-1'): Promise<void> {
	let approved = false;
	await page.route(/\/api\/runs\/[^/?]+\/approve$/, (route) => {
		approved = true;
		return route.fulfill({ status: 200, json: { status: 'approved' } });
	});
	await page.route(/\/api\/runs\/[^/?]+\/reject$/, (route) =>
		route.fulfill({ status: 200, json: { status: 'rejected' } })
	);
	// coverage sidebar is best-effort; 404 keeps it out of the way (the client swallows it)
	await page.route(/\/api\/coverage/, (route) => route.fulfill({ status: 404, json: {} }));
	await page.route(detailUrl, (route) =>
		route.fulfill({
			json: {
				id,
				pipeline: 'kontinuance-service',
				status: approved ? 'Success' : 'WaitingOnApproval',
				repo: 'khorum-oss/kontinuance',
				sha: 'c0ffee12',
				startedAt: '2026-07-17T00:00:00Z'
			}
		})
	);
}

/** Serve the coverage stub (Kover-shaped) for the coverage screen. */
export async function mockCoverage(page: Page): Promise<void> {
	await page.route(/\/api\/coverage/, (route) =>
		route.fulfill({
			json: {
				tool: 'kover',
				line: { pct: '84.2%', covered: 4821, total: 5724 },
				branch: { pct: '72.1%', covered: 611, total: 848 },
				classes: 142,
				modules: [
					{ name: 'engine', kind: 'module', linePct: 91, branchPct: 84, missed: 214 },
					{ name: 'server', kind: 'module', linePct: 86, branchPct: 74, missed: 63 }
				]
			}
		})
	);
}

/** Serve the pipeline stub for the pipeline screen. */
export async function mockPipeline(page: Page): Promise<void> {
	await page.route(/\/api\/runs\/[^/?]+\/pipeline/, (route) =>
		route.fulfill({
			json: {
				runId: '#KX-2046',
				stages: [
					{ id: 's1', name: 'CHECKOUT', tasks: [{ id: 'git', name: 'git checkout', tool: 'git', status: 'success', progress: 100, deps: [] }] },
					{
						id: 's3',
						name: 'BUILD',
						tasks: [
							{ id: 'core', name: ':core assemble', tool: 'gradle', status: 'success', progress: 100, deps: [] },
							{ id: 'legacy', name: 'legacy-adapter package', tool: 'maven', status: 'running', progress: 62, deps: ['core'] }
						]
					},
					{ id: 's4', name: 'TEST', tasks: [{ id: 'unit', name: 'unit tests', tool: 'gradle', status: 'running', progress: 40, deps: ['core'] }] }
				]
			}
		})
	);
}

/** Serve the deploy stub for the deploy screen. */
export async function mockDeploy(page: Page): Promise<void> {
	await page.route(/\/api\/deploy/, (route) =>
		route.fulfill({
			json: {
				nodes: [
					{ id: 'build', label: 'SOURCE', title: 'kontinuance-service', status: 'synced', meta: 'commit a3f19c2' },
					{ id: 'stage', label: 'STAGE', title: 'argocd / kontinuance-stage', status: 'progressing', meta: 'rollout 2/3' },
					{ id: 'prod', label: 'PROD', title: 'manual promotion gate', status: 'pending', meta: 'awaiting approval' }
				],
				artifacts: [
					{ kind: 'JAR', name: 'kontinuance-core-1.4.2.jar', digest: 'sha256:8c1e42aa', state: 'published' },
					{ kind: 'OCI', name: 'kontinuance:1.4.2', digest: 'sha256:8c1e42aa', state: 'pushed' }
				],
				environment: { podsReady: '2/3', syncRevision: '1.4.2', health: 'Progressing', meta: 'namespace kontinuance-stage' }
			}
		})
	);
}

/** Serve the config stub for the config screen. */
export async function mockConfig(page: Page): Promise<void> {
	await page.route(/\/api\/config/, (route) =>
		route.fulfill({
			json: {
				source: 'kontinuance.yml',
				text: '# kontinuance.yml — pipeline definition\nversion: 0.4\nproject: kontinuance-service',
				plan: {
					stages: 6,
					tasks: 10,
					maxParallel: 3,
					toolchain: 'temurin-21 · gradle 8.8',
					publish: 'nexus.internal',
					deploy: 'argocd / kontinuance-stage'
				}
			}
		})
	);
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
