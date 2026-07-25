// Live run stream over Server-Sent Events (`/api/runs/stream`). Exposes a Svelte store that accumulates
// run records as they arrive (upsert by id) and tracks a `degraded` flag when the connection drops.
// EventSource auto-reconnects; `degraded` clears on the next successful open. The server also offers a
// WebSocket (`/ws/runs`) with the same records — SSE is used here for its built-in reconnect.

import { readable, type Readable } from 'svelte/store';
import type { RunRecord } from './types';

export interface LiveState {
	/** Records seen so far, in arrival order (callers sort for display). */
	runs: RunRecord[];
	connected: boolean;
	degraded: boolean;
}

const INITIAL: LiveState = { runs: [], connected: false, degraded: false };

export function runStream(url = '/api/runs/stream'): Readable<LiveState> {
	return readable<LiveState>(INITIAL, (set) => {
		// No EventSource under SSR / tests without a DOM — yield an inert stream.
		if (typeof EventSource === 'undefined') return () => {};

		const byId = new Map<string, RunRecord>();
		let state: LiveState = INITIAL;
		const push = (next: Partial<LiveState>) => {
			state = { ...state, ...next };
			set(state);
		};

		const es = new EventSource(url);
		es.addEventListener('run', (event) => {
			try {
				const record = JSON.parse((event as MessageEvent).data) as RunRecord;
				if (!record?.id) return;
				byId.set(record.id, record);
				push({ runs: [...byId.values()], connected: true, degraded: false });
			} catch {
				// ignore a malformed frame; the next one recovers
			}
		});
		es.onopen = () => push({ connected: true, degraded: false });
		es.onerror = () => push({ degraded: true });

		return () => es.close();
	});
}

export interface LogTailState {
	/** Lines received so far, in order. */
	lines: string[];
	/** True once the run reached a terminal state and the server closed the tail (the `end` event). */
	done: boolean;
}

const LOG_INITIAL: LogTailState = { lines: [], done: false };

/**
 * Live tail of one run's output over Server-Sent Events (`/api/runs/{id}/logs/stream`, 023). Each `log`
 * event carries one line; a terminal `end` event fires once the run finishes, after which we close the
 * connection so EventSource does not reconnect. On a mid-run reconnect the server replays from the start,
 * so we reset the accumulated lines on each `open` to avoid duplicates.
 */
export function logStream(id: string): Readable<LogTailState> {
	const url = `/api/runs/${encodeURIComponent(id)}/logs/stream`;
	return readable<LogTailState>(LOG_INITIAL, (set) => {
		// No EventSource under SSR / tests without a DOM — yield an inert stream.
		if (typeof EventSource === 'undefined') return () => {};

		let lines: string[] = [];
		const es = new EventSource(url);
		es.addEventListener('open', () => {
			lines = [];
			set({ lines, done: false });
		});
		es.addEventListener('log', (event) => {
			lines = [...lines, (event as MessageEvent).data];
			set({ lines, done: false });
		});
		es.addEventListener('end', () => {
			set({ lines, done: true });
			es.close();
		});

		return () => es.close();
	});
}
