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
