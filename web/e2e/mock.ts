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

const logStreamUrl = /\/api\/runs\/[^/?]+\/logs\/stream/;

/** SSE body for the live log tail (023): each line as a `log` event, then a terminal `end` event. */
function logSseBody(lines: string[]): string {
	return lines.map((l) => `event: log\ndata: ${l}\n\n`).join('') + 'event: end\ndata: \n\n';
}

/** Mock the per-run live log tail (`/api/runs/{id}/logs/stream`) to emit `lines` then `end`. */
export async function mockLogStream(page: Page, lines: string[]): Promise<void> {
	await page.route(logStreamUrl, (route) =>
		route.fulfill({ contentType: 'text/event-stream', body: logSseBody(lines) })
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
	await page.route(/\/api\/runs\/[^/?]+\/logs$/, (route) =>
		route.fulfill({ json: { runId: 'x', lines: ['[build] compiling', '[test] 12 passed'] } })
	);
	await mockLogStream(page, ['[build] compiling', '[test] 12 passed']);
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
	await page.route(/\/api\/runs\/[^/?]+\/logs$/, (route) =>
		route.fulfill({ json: { runId: 'x', lines: [] } })
	);
	await mockLogStream(page, []);
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

/** Serve a single running run whose detail can be cancelled; cancelling flips it to Cancelled (028). */
export async function mockCancelableRun(page: Page, id = 'run-cancel-1'): Promise<void> {
	let canceled = false;
	await page.route(/\/api\/runs\/[^/?]+\/cancel$/, (route) => {
		canceled = true;
		return route.fulfill({ status: 200, json: { status: 'cancelling' } });
	});
	// coverage sidebar is best-effort; 404 keeps it out of the way
	await page.route(/\/api\/coverage/, (route) => route.fulfill({ status: 404, json: {} }));
	await page.route(/\/api\/runs\/[^/?]+\/logs$/, (route) => route.fulfill({ json: { runId: 'x', lines: [] } }));
	await mockLogStream(page, []);
	await page.route(detailUrl, (route) =>
		route.fulfill({
			json: {
				id,
				pipeline: 'kontinuance-service',
				status: canceled ? 'Cancelled' : 'Running',
				repo: 'khorum-oss/kontinuance',
				sha: 'abc123de1',
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
					{
						name: 'engine',
						kind: 'module',
						linePct: 91,
						branchPct: 84,
						missed: 214,
						classes: [
							{ name: 'execution.DefaultPipelineEngine', linePct: 88, branchPct: 79, missed: 96 },
							{ name: 'model.Step', linePct: 97, branchPct: 92, missed: 4 }
						]
					},
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
				// run-derived shape (022): SOURCE node + a node per real stage; no artifact registry (external)
				nodes: [
					{ id: 'source', label: 'SOURCE', title: 'khorum-oss/kontinuance', status: 'synced', meta: 'commit a3f19c2' },
					{ id: 'stage-0', label: 'BUILD', title: '1 step', status: 'synced', meta: 'assemble — synced' },
					{ id: 'stage-1', label: 'TEST', title: '1 step', status: 'failed', meta: 'unit — failed' }
				],
				artifacts: [],
				environment: {
					podsReady: '1/2',
					syncRevision: 'a3f19c2',
					health: 'FAILED',
					meta: 'Registry & ArgoCD sync are external — not tracked by Kontinuance.'
				}
			}
		})
	);
}

const configPlan = {
	stages: 6,
	tasks: 10,
	maxParallel: 3,
	toolchain: 'temurin-21 · gradle 8.8',
	publish: 'nexus.internal',
	deploy: 'argocd / kontinuance-stage'
};

/**
 * Serve the config screen. `GET` returns the fixture descriptor; `PUT` (027) echoes the edited text back
 * as the refreshed config, or rejects it 400 when the text contains `BROKEN` (a stand-in for the server's
 * strict-parser validation) so the editor's inline-error path can be exercised.
 */
export async function mockConfig(page: Page): Promise<void> {
	await page.route(/\/api\/config/, (route) => {
		if (route.request().method() === 'PUT') {
			const body = JSON.parse(route.request().postData() ?? '{}') as { text?: string };
			const text = body.text ?? '';
			if (text.includes('BROKEN')) {
				return route.fulfill({
					status: 400,
					json: { error: 'pipeline.stages[0]: unknown key(s) [BROKEN]' }
				});
			}
			return route.fulfill({ json: { source: 'kontinuance.yml', text, plan: configPlan } });
		}
		return route.fulfill({
			json: {
				source: 'kontinuance.yml',
				text: '# kontinuance.yml — pipeline definition\nversion: 0.4\nproject: kontinuance-service',
				plan: configPlan
			}
		});
	});
}

/**
 * Serve the named-project registry (032): the project list + add + activate. Seeded with two projects
 * (`kontinuance-service` active, `infra-charts` available). `POST /api/projects` appends a project —
 * rejecting a bad-slug name (400), a duplicate (409), and a descriptor containing `BROKEN` (400, a
 * stand-in for the server's strict-parser validation). Activate flips which project is active. State is
 * in-memory so the add + reload flow reflects the new project.
 */
export async function mockProjects(page: Page): Promise<void> {
	const projects = [
		{ name: 'kontinuance-service', active: true },
		{ name: 'infra-charts', active: false }
	];
	await page.route(/\/api\/projects\/[^/?]+\/activate$/, (route) => {
		const name = decodeURIComponent(new URL(route.request().url()).pathname.split('/').slice(-2)[0]);
		projects.forEach((p) => (p.active = p.name === name));
		return route.fulfill({ json: { active: name } });
	});
	await page.route(/\/api\/projects$/, (route) => {
		if (route.request().method() === 'POST') {
			const body = JSON.parse(route.request().postData() ?? '{}') as { name?: string; text?: string };
			const name = body.name ?? '';
			const text = body.text ?? '';
			if (!/^[A-Za-z0-9._-]{1,64}$/.test(name)) {
				return route.fulfill({ status: 400, json: { error: 'invalid project name' } });
			}
			if (projects.some((p) => p.name === name)) {
				return route.fulfill({ status: 409, json: { error: `project already exists: ${name}` } });
			}
			if (text.includes('BROKEN')) {
				return route.fulfill({ status: 400, json: { error: 'invalid descriptor' } });
			}
			projects.push({ name, active: false });
			return route.fulfill({ json: { name } });
		}
		const active = projects.find((p) => p.active)?.name ?? null;
		return route.fulfill({ json: { active, projects } });
	});
}

/** Make every runs request fail, to exercise the error state. */
export async function mockApiError(page: Page): Promise<void> {
	await page.route(/\/api\/runs/, (route) =>
		route.fulfill({ status: 502, json: { error: 'bad gateway' } })
	);
}

/**
 * Mock the 016 auth endpoints. Default: auth is enforced but no session yet (`/api/auth/me` → 401), a
 * correct sign-in succeeds, and a password of `wrong` is rejected — so tests can drive both the happy path
 * and the error path. Pass `{ authRequired: false }` for open mode (no sign-in required).
 */
export async function mockAuth(
	page: Page,
	opts: { authRequired?: boolean; username?: string } = {}
): Promise<void> {
	const authRequired = opts.authRequired ?? true;
	const username = opts.username ?? 'mkuraja';
	await page.route(/\/api\/auth\/me$/, (route) =>
		authRequired
			? route.fulfill({ status: 401, json: { authenticated: false, authRequired: true } })
			: route.fulfill({ json: { authenticated: false, authRequired: false } })
	);
	await page.route(/\/api\/auth\/login$/, (route) => {
		const body = JSON.parse(route.request().postData() ?? '{}') as { username?: string; password?: string };
		return body.password === 'wrong'
			? route.fulfill({
					status: 401,
					json: { authenticated: false, authRequired: true, error: 'invalid credentials' }
				})
			: route.fulfill({
					json: { authenticated: true, authRequired: true, username: body.username || username }
				});
	});
	await page.route(/\/api\/auth\/logout$/, (route) =>
		route.fulfill({ json: { authenticated: false } })
	);
}

/**
 * Drive the entry flow against the mocked server (sign in → activate a project to enter mission control).
 * Requires [mockProjects] to be in effect (registered in the suite's beforeEach).
 */
export async function enterApp(page: Page): Promise<void> {
	await page.getByPlaceholder('username').fill('mkuraja');
	await page.getByPlaceholder('password').fill('s3cret');
	await page.getByText('SIGN IN', { exact: true }).click();
	await page.getByText('kontinuance-service', { exact: true }).click();
}
