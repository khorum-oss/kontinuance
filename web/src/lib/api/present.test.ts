import { describe, expect, it } from 'vitest';
import { filterRuns, mergeNewestFirst, runMessage, runRef, toRunView } from './present';
import type { RunRecord } from './types';

const base: RunRecord = { id: '#KX-1', pipeline: 'kontinuance-service', status: 'Success' };

describe('runRef', () => {
	it('joins repo and short sha', () => {
		expect(runRef({ ...base, repo: 'khorum-oss/kontinuance', sha: 'a3f19c2abc' })).toBe(
			'khorum-oss/kontinuance · a3f19c2'
		);
	});

	it('falls back to the trigger, then an em dash', () => {
		expect(runRef({ ...base, trigger: 'manual' })).toBe('manual');
		expect(runRef(base)).toBe('—');
	});
});

describe('runMessage', () => {
	it('uses the pipeline name for a non-failed run', () => {
		expect(runMessage(base)).toBe('kontinuance-service');
	});

	it('surfaces the failing step and reason for a failed run', () => {
		expect(
			runMessage({ ...base, status: 'Failed', failingStep: 'integration tests', reason: '2 failed' })
		).toBe('integration tests: 2 failed');
	});
});

describe('toRunView', () => {
	it('marks a running run indeterminate with no age/duration when unfinished', () => {
		const v = toRunView({ ...base, status: 'Running', startedAt: '2026-07-17T00:00:00Z' });
		expect(v.status).toBe('running');
		expect(v.indeterminate).toBe(true);
		expect(v.progress).toBeGreaterThan(0);
	});

	it('computes duration and age for a finished run', () => {
		const now = Date.parse('2026-07-17T01:00:00Z');
		const v = toRunView(
			{
				...base,
				status: 'Success',
				startedAt: '2026-07-17T00:54:59Z',
				endedAt: '2026-07-17T01:00:00Z'
			},
			now
		);
		expect(v.progress).toBe(100);
		expect(v.indeterminate).toBe(false);
		expect(v.duration).toBe('5m 01s');
		expect(v.age).toBe('0s');
	});

	it('shows em dashes when timestamps are missing', () => {
		const v = toRunView(base, Date.parse('2026-07-17T01:00:00Z'));
		expect(v.duration).toBe('—');
		expect(v.age).toBe('—');
	});
});

describe('mergeNewestFirst', () => {
	it('dedupes by id (later wins) and orders by latest activity', () => {
		const merged = mergeNewestFirst([
			{ ...base, id: '#A', endedAt: '2026-07-17T00:10:00Z' },
			{ ...base, id: '#B', endedAt: '2026-07-17T00:30:00Z' },
			{ ...base, id: '#A', status: 'Failed', endedAt: '2026-07-17T00:20:00Z' }
		]);
		expect(merged.map((r) => r.id)).toEqual(['#B', '#A']);
		expect(merged.find((r) => r.id === '#A')?.status).toBe('Failed');
	});

	it('treats a running run (start only) as newer than an older finished run', () => {
		const merged = mergeNewestFirst([
			{ ...base, id: '#done', endedAt: '2026-07-17T00:00:00Z' },
			{ ...base, id: '#live', status: 'Running', startedAt: '2026-07-17T00:05:00Z' }
		]);
		expect(merged[0].id).toBe('#live');
	});
});

describe('filterRuns', () => {
	const runs: RunRecord[] = [
		{ id: '#KX-1', pipeline: 'svc-a', status: 'Success', repo: 'khorum-oss/a', sha: 'a3f19c2ff', trigger: 'manual' },
		{ id: '#KX-2', pipeline: 'svc-b', status: 'Failed', repo: 'khorum-oss/b', sha: '77aa310aa', trigger: 'PULL_REQUEST' },
		{ id: '#KX-3', pipeline: 'svc-a', status: 'Running', repo: 'khorum-oss/a', sha: '9b02d1e00', trigger: 'PUSH' }
	];
	const noFilter = { query: '', status: 'all', trigger: 'all' };

	it('returns everything with no active criteria', () => {
		expect(filterRuns(runs, noFilter).map((r) => r.id)).toEqual(['#KX-1', '#KX-2', '#KX-3']);
	});

	it('filters by canonical status', () => {
		expect(filterRuns(runs, { ...noFilter, status: 'failed' }).map((r) => r.id)).toEqual(['#KX-2']);
	});

	it('filters by trigger, case-insensitively', () => {
		expect(filterRuns(runs, { ...noFilter, trigger: 'push' }).map((r) => r.id)).toEqual(['#KX-3']);
		expect(filterRuns(runs, { ...noFilter, trigger: 'pull_request' }).map((r) => r.id)).toEqual(['#KX-2']);
	});

	it('searches id, pipeline, repo, and commit (case-insensitive substring)', () => {
		expect(filterRuns(runs, { ...noFilter, query: '77aa310' }).map((r) => r.id)).toEqual(['#KX-2']);
		expect(filterRuns(runs, { ...noFilter, query: 'SVC-A' }).map((r) => r.id)).toEqual(['#KX-1', '#KX-3']);
		expect(filterRuns(runs, { ...noFilter, query: 'kx-2' }).map((r) => r.id)).toEqual(['#KX-2']);
	});

	it('composes filters with AND', () => {
		expect(filterRuns(runs, { query: 'svc-a', status: 'running', trigger: 'push' }).map((r) => r.id)).toEqual([
			'#KX-3'
		]);
		expect(filterRuns(runs, { query: 'svc-a', status: 'failed', trigger: 'all' })).toEqual([]);
	});

	it('treats a blank/whitespace query as matching everything', () => {
		expect(filterRuns(runs, { ...noFilter, query: '   ' })).toHaveLength(3);
	});

	it('matches a run missing repo/sha when the query is empty', () => {
		const bare: RunRecord = { id: '#KX-9', pipeline: 'p', status: 'Success' };
		expect(filterRuns([bare], noFilter)).toHaveLength(1);
		expect(filterRuns([bare], { ...noFilter, query: 'p' })).toHaveLength(1);
	});
});
