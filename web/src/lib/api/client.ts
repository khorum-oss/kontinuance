// HTTP client for the Kontinuance server. Same-origin in production; the dev server proxies /api to the
// server (see vite.config.ts). Every call throws [ApiError] on a non-2xx or transport failure so screens
// can render a clear error state.

import type { Config, Coverage, Deploy, Pipeline, RunRecord, RunsResponse, Session } from './types';

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

// POST an action on a run (approve/reject); resolves on 2xx, throws [ApiError] with the server's
// `error` message otherwise.
async function postRun(id: string, action: 'approve' | 'reject'): Promise<void> {
	let res: Response;
	try {
		res = await fetch(`/api/runs/${encodeURIComponent(id)}/${action}`, {
			method: 'POST',
			headers: { accept: 'application/json' }
		});
	} catch (e) {
		throw new ApiError(`cannot reach the server (${(e as Error).message})`);
	}
	if (!res.ok) {
		const body = (await res.json().catch(() => ({}))) as { error?: string };
		throw new ApiError(body.error ?? `request failed: ${res.status} ${res.statusText}`, res.status);
	}
}

export const api = {
	health: () => getJson<{ status: string }>('/api/health'),

	// Current session (016). `/api/auth/me` is public: it answers 200 in open mode and when signed in, and
	// 401 (with an `authRequired` body) when auth is enforced but there is no session. A 401 here is a
	// normal "not signed in" answer, not a transport error, so it is mapped rather than thrown.
	me: async (): Promise<Session> => {
		let res: Response;
		try {
			res = await fetch('/api/auth/me', { headers: { accept: 'application/json' } });
		} catch (e) {
			throw new ApiError(`cannot reach the server (${(e as Error).message})`);
		}
		if (res.status === 401) {
			const body = (await res.json().catch(() => ({}))) as { authRequired?: boolean };
			return { authenticated: false, authRequired: body.authRequired ?? true };
		}
		if (!res.ok) {
			throw new ApiError(`request failed: ${res.status} ${res.statusText}`, res.status);
		}
		return (await res.json()) as Session;
	},

	// Sign in. Resolves to the new [Session] on success; throws [ApiError] with the server's `error`
	// message (e.g. "invalid credentials") on 401. The server sets the HttpOnly KSESSION cookie.
	login: async (username: string, password: string): Promise<Session> => {
		let res: Response;
		try {
			res = await fetch('/api/auth/login', {
				method: 'POST',
				headers: { 'content-type': 'application/json', accept: 'application/json' },
				body: JSON.stringify({ username, password })
			});
		} catch (e) {
			throw new ApiError(`cannot reach the server (${(e as Error).message})`);
		}
		const body = (await res.json().catch(() => ({}))) as Session & { error?: string };
		if (!res.ok) {
			throw new ApiError(body.error ?? `sign-in failed: ${res.status} ${res.statusText}`, res.status);
		}
		return body;
	},

	// End the session (best-effort). Logout always succeeds server-side; a transport failure is ignored.
	logout: async (): Promise<void> => {
		try {
			await fetch('/api/auth/logout', { method: 'POST', headers: { accept: 'application/json' } });
		} catch {
			// best-effort: the local view resets regardless
		}
	},

	listRuns: async (limit?: number): Promise<RunRecord[]> => {
		const q = limit ? `?limit=${limit}` : '';
		const body = await getJson<RunsResponse>(`/api/runs${q}`);
		return body.runs ?? [];
	},

	getRun: (id: string) => getJson<RunRecord>(`/api/runs/${encodeURIComponent(id)}`),

	// Starts a run of the configured pipeline. Resolves to the new run's id (202); throws [ApiError]
	// with the server's `error` message when no valid descriptor is configured (400) or on transport failure.
	triggerRun: async (): Promise<string> => {
		let res: Response;
		try {
			res = await fetch('/api/runs/trigger', { method: 'POST', headers: { accept: 'application/json' } });
		} catch (e) {
			throw new ApiError(`cannot reach the server (${(e as Error).message})`);
		}
		const body = (await res.json().catch(() => ({}))) as { runId?: string; error?: string };
		if (!res.ok) {
			throw new ApiError(body.error ?? `request failed: ${res.status} ${res.statusText}`, res.status);
		}
		return body.runId ?? '';
	},

	// Resolves a run paused at a manual-approval gate. Throws [ApiError] (404 with the server's message)
	// when no run with that id is currently waiting.
	approveRun: (id: string) => postRun(id, 'approve'),
	rejectRun: (id: string) => postRun(id, 'reject'),

	// forward-looking screens (stub endpoints; see contracts/stub-api.md)
	getPipeline: (runId: string) => getJson<Pipeline>(`/api/runs/${encodeURIComponent(runId)}/pipeline`),
	getDeploy: () => getJson<Deploy>('/api/deploy'),
	getCoverage: () => getJson<Coverage>('/api/coverage'),
	getConfig: () => getJson<Config>('/api/config')
};
