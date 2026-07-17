import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './client';

function mockFetch(impl: (url: string) => Response | Promise<Response>) {
	vi.stubGlobal(
		'fetch',
		vi.fn((input: string | URL | Request) => Promise.resolve(impl(String(input))))
	);
}

function json(body: unknown, init?: ResponseInit): Response {
	return new Response(JSON.stringify(body), {
		status: 200,
		headers: { 'content-type': 'application/json' },
		...init
	});
}

afterEach(() => vi.unstubAllGlobals());

describe('api.listRuns', () => {
	it('requests the runs endpoint with the limit and returns the array', async () => {
		const seen: string[] = [];
		mockFetch((url) => {
			seen.push(url);
			return json({ runs: [{ id: '#KX-1', pipeline: 'p', status: 'Success' }] });
		});
		const runs = await api.listRuns(25);
		expect(seen[0]).toBe('/api/runs?limit=25');
		expect(runs).toHaveLength(1);
		expect(runs[0].id).toBe('#KX-1');
	});

	it('tolerates a missing runs field', async () => {
		mockFetch(() => json({}));
		expect(await api.listRuns()).toEqual([]);
	});
});

describe('api.getRun', () => {
	it('encodes the id in the path', async () => {
		const seen: string[] = [];
		mockFetch((url) => {
			seen.push(url);
			return json({ id: '#KX 9', pipeline: 'p', status: 'Success' });
		});
		await api.getRun('#KX 9');
		expect(seen[0]).toBe('/api/runs/%23KX%209');
	});
});

describe('api.triggerRun', () => {
	it('POSTs the trigger endpoint and returns the new run id', async () => {
		const seen: { url: string; method?: string }[] = [];
		vi.stubGlobal(
			'fetch',
			vi.fn((input: string | URL | Request, init?: RequestInit) => {
				seen.push({ url: String(input), method: init?.method });
				return Promise.resolve(json({ runId: 'run-abcd1234' }, { status: 202 }));
			})
		);
		const id = await api.triggerRun();
		expect(seen[0].url).toBe('/api/runs/trigger');
		expect(seen[0].method).toBe('POST');
		expect(id).toBe('run-abcd1234');
	});

	it('throws ApiError carrying the server message on a 400', async () => {
		mockFetch(() => json({ error: 'no pipeline descriptor' }, { status: 400, statusText: 'Bad Request' }));
		const err = await api.triggerRun().catch((e) => e);
		expect(err).toBeInstanceOf(ApiError);
		expect(err.status).toBe(400);
		expect(err.message).toBe('no pipeline descriptor');
	});
});

describe('api.approveRun / rejectRun', () => {
	it('POSTs the approve action on the run path', async () => {
		const seen: { url: string; method?: string }[] = [];
		vi.stubGlobal(
			'fetch',
			vi.fn((input: string | URL | Request, init?: RequestInit) => {
				seen.push({ url: String(input), method: init?.method });
				return Promise.resolve(json({ status: 'approved' }));
			})
		);
		await api.approveRun('#KX 9');
		expect(seen[0].url).toBe('/api/runs/%23KX%209/approve');
		expect(seen[0].method).toBe('POST');
	});

	it('throws ApiError with the server message when nothing is waiting', async () => {
		mockFetch(() =>
			json({ error: 'no run awaiting approval' }, { status: 404, statusText: 'Not Found' })
		);
		const err = await api.rejectRun('x').catch((e) => e);
		expect(err).toBeInstanceOf(ApiError);
		expect(err.status).toBe(404);
		expect(err.message).toBe('no run awaiting approval');
	});
});

describe('error handling', () => {
	it('throws ApiError with the status on a non-2xx response', async () => {
		mockFetch(() => json({ error: 'not found' }, { status: 404, statusText: 'Not Found' }));
		await expect(api.getRun('missing')).rejects.toMatchObject({
			name: 'ApiError',
			status: 404
		});
	});

	it('throws ApiError on a transport failure', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn(() => Promise.reject(new Error('network down')))
		);
		const err = await api.health().catch((e) => e);
		expect(err).toBeInstanceOf(ApiError);
		expect(err.message).toContain('cannot reach the server');
	});
});
