// HTTP client for the Kontinuance server. Same-origin in production; the dev server proxies /api to the
// server (see vite.config.ts). Every call throws [ApiError] on a non-2xx or transport failure so screens
// can render a clear error state.

import type { Config, Coverage, Deploy, Pipeline, RunRecord, RunsResponse } from './types';

export class ApiError extends Error {
	constructor(
		message: string,
		readonly status?: number
	) {
		super(message);
		this.name = 'ApiError';
	}
}

async function getJson<T>(path: string): Promise<T> {
	let res: Response;
	try {
		res = await fetch(path, { headers: { accept: 'application/json' } });
	} catch (e) {
		throw new ApiError(`cannot reach the server (${(e as Error).message})`);
	}
	if (!res.ok) {
		throw new ApiError(`request failed: ${res.status} ${res.statusText}`, res.status);
	}
	return (await res.json()) as T;
}

export const api = {
	health: () => getJson<{ status: string }>('/api/health'),

	listRuns: async (limit?: number): Promise<RunRecord[]> => {
		const q = limit ? `?limit=${limit}` : '';
		const body = await getJson<RunsResponse>(`/api/runs${q}`);
		return body.runs ?? [];
	},

	getRun: (id: string) => getJson<RunRecord>(`/api/runs/${encodeURIComponent(id)}`),

	// forward-looking screens (stub endpoints; see contracts/stub-api.md)
	getPipeline: (runId: string) => getJson<Pipeline>(`/api/runs/${encodeURIComponent(runId)}/pipeline`),
	getDeploy: () => getJson<Deploy>('/api/deploy'),
	getCoverage: () => getJson<Coverage>('/api/coverage'),
	getConfig: () => getJson<Config>('/api/config')
};
