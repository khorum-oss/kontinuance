import { afterEach, describe, expect, it, vi } from 'vitest';
import { get } from 'svelte/store';
import { runStream, type LiveState } from './live';
import type { RunRecord } from './types';

class MockEventSource {
	static instances: MockEventSource[] = [];
	listeners: Record<string, ((e: unknown) => void)[]> = {};
	onopen: (() => void) | null = null;
	onerror: (() => void) | null = null;
	closed = false;
	constructor(readonly url: string) {
		MockEventSource.instances.push(this);
	}
	addEventListener(type: string, cb: (e: unknown) => void) {
		(this.listeners[type] ??= []).push(cb);
	}
	emit(record: RunRecord) {
		for (const cb of this.listeners['run'] ?? []) cb({ data: JSON.stringify(record) });
	}
	close() {
		this.closed = true;
	}
}

function run(id: string, status = 'Running'): RunRecord {
	return { id, pipeline: 'p', status };
}

afterEach(() => {
	vi.unstubAllGlobals();
	MockEventSource.instances = [];
});

describe('runStream', () => {
	it('accumulates streamed records (upsert by id) and marks connected', () => {
		vi.stubGlobal('EventSource', MockEventSource);
		const store = runStream();
		const unsub = store.subscribe(() => {}); // activate the source
		const es = MockEventSource.instances[0];

		es.emit(run('#A'));
		es.emit(run('#B'));
		es.emit(run('#A', 'Success')); // update existing

		const state = get(store) as LiveState;
		expect(state.runs.map((r) => r.id)).toEqual(['#A', '#B']);
		expect(state.runs.find((r) => r.id === '#A')?.status).toBe('Success');
		expect(state.connected).toBe(true);
		expect(state.degraded).toBe(false);
		unsub();
	});

	it('flags degraded on error and clears it on reopen', () => {
		vi.stubGlobal('EventSource', MockEventSource);
		const store = runStream();
		const unsub = store.subscribe(() => {});
		const es = MockEventSource.instances[0];

		es.onerror?.();
		expect((get(store) as LiveState).degraded).toBe(true);

		es.onopen?.();
		expect((get(store) as LiveState).degraded).toBe(false);
		unsub();
	});

	it('closes the connection when the last subscriber leaves', () => {
		vi.stubGlobal('EventSource', MockEventSource);
		const store = runStream();
		const unsub = store.subscribe(() => {});
		const es = MockEventSource.instances[0];
		unsub();
		expect(es.closed).toBe(true);
	});

	it('is inert when EventSource is unavailable', () => {
		vi.stubGlobal('EventSource', undefined);
		const store = runStream();
		const unsub = store.subscribe(() => {});
		expect(get(store).runs).toEqual([]);
		unsub();
	});
});
