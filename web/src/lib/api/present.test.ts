import { describe, expect, it } from 'vitest';
import { mergeNewestFirst, runMessage, runRef, toRunView } from './present';
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
