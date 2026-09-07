import { describe, expect, it } from 'vitest';
import {
	descriptorCheckMessage,
	descriptorOriginLabel,
	filterRuns,
	lastRunAge,
	matchesRunFilter,
	mergeNewestFirst,
	runMessage,
	runRef,
	runWorkSummary,
	sourceCheckoutOnlyNote,
	toRunView
} from './present';
import type { RunRecord } from './types';

const base: RunRecord = { id: '#KX-1', pipeline: 'kontinuance-service', status: 'Success' };

// The exact shape ProjectSourceInjector prepends when a descriptor declares no checkout of its own.
const synthesizedCheckout = {
	name: 'checkout',
	status: 'Success',
	steps: [{ name: 'checkout', status: 'Success', tool: 'git' }]
};

describe('runWorkSummary', () => {
	it('counts the stages and steps the run executed', () => {
		expect(
			runWorkSummary({
				...base,
				stages: [
					{ name: 'build', status: 'Success', steps: [{ name: 'compile', status: 'Success' }] },
					{
						name: 'test',
						status: 'Success',
						steps: [
							{ name: 'unit', status: 'Success' },
							{ name: 'e2e', status: 'Success' }
						]
					}
				]
			})
		).toBe('2 stages · 3 steps');
	});

	it('uses singular wording for a single stage and step', () => {
		expect(runWorkSummary({ ...base, stages: [synthesizedCheckout] })).toBe('1 stage · 1 step');
	});

	it('is empty for a record with no stage roll-up, so the caller can omit the line', () => {
		expect(runWorkSummary(base)).toBe('');
		expect(runWorkSummary({ ...base, stages: [] })).toBe('');
	});

	it('tolerates a stage whose steps are absent', () => {
		expect(runWorkSummary({ ...base, stages: [{ name: 'build', status: 'Success' }] })).toBe(
			'1 stage · 0 steps'
		);
	});
});

describe('sourceCheckoutOnlyNote', () => {
	it('explains a run whose only stage was the synthesized source checkout', () => {
		const note = sourceCheckoutOnlyNote({ ...base, stages: [synthesizedCheckout] });
		expect(note).toContain('no stages');
	});

	it('stays silent when the pipeline declared real work alongside the checkout', () => {
		expect(
			sourceCheckoutOnlyNote({
				...base,
				stages: [synthesizedCheckout, { name: 'build', status: 'Success', steps: [] }]
			})
		).toBeNull();
	});

	it('stays silent for a lone stage that is not a git checkout', () => {
		expect(
			sourceCheckoutOnlyNote({
				...base,
				stages: [
					{ name: 'build', status: 'Success', steps: [{ name: 'compile', status: 'Success' }] }
				]
			})
		).toBeNull();
	});

	it('stays silent for a checkout stage that ran more than one step', () => {
		expect(
			sourceCheckoutOnlyNote({
				...base,
				stages: [
					{
						name: 'checkout',
						status: 'Success',
						steps: [
							{ name: 'checkout', status: 'Success', tool: 'git' },
							{ name: 'submodules', status: 'Success', tool: 'git' }
						]
					}
				]
			})
		).toBeNull();
	});

	it('stays silent for a record with no stage roll-up', () => {
		expect(sourceCheckoutOnlyNote(base)).toBeNull();
	});
});

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

describe('matchesRunFilter project scoping', () => {
	const base = { id: 'r1', pipeline: 'relikquary-pr', status: 'Success' };
	const all = { query: '', status: 'all', trigger: 'all', project: 'all' };

	it('matches every run when the project filter is all', () => {
		expect(matchesRunFilter({ ...base, repo: 'khorum-oss/relikquary' }, all)).toBe(true);
	});

	it('matches on the explicit project', () => {
		const r = { ...base, project: 'relikquary', repo: 'khorum-oss/other' };
		expect(matchesRunFilter(r, { ...all, project: 'relikquary' })).toBe(true);
		expect(matchesRunFilter(r, { ...all, project: 'other' })).toBe(false);
	});

	it('falls back to the repository short name', () => {
		const r = { ...base, repo: 'khorum-oss/relikquary' };
		expect(matchesRunFilter(r, { ...all, project: 'relikquary' })).toBe(true);
	});

	it('excludes a run that resolves to no project when one is selected', () => {
		expect(matchesRunFilter(base, { ...all, project: 'relikquary' })).toBe(false);
	});

	it('composes with the status filter', () => {
		const r = { ...base, status: 'Failed', repo: 'khorum-oss/relikquary' };
		expect(matchesRunFilter(r, { ...all, project: 'relikquary', status: 'success' })).toBe(false);
	});

	it('treats a missing project key as matching every run (project is optional)', () => {
		const r = { ...base, repo: 'khorum-oss/relikquary' };
		const filterWithoutProjectKey = { query: '', status: 'all', trigger: 'all' };
		expect(matchesRunFilter(r, filterWithoutProjectKey)).toBe(true);
		expect(matchesRunFilter(base, filterWithoutProjectKey)).toBe(true);
	});
});

describe('lastRunAge', () => {
	const now = Date.parse('2026-07-17T12:00:00Z');

	it('formats how long ago the last run finished', () => {
		expect(lastRunAge('2026-07-17T10:00:00Z', now)).toBe('2h');
	});

	it('is an em dash when there is no last-run timestamp', () => {
		// A project whose newest run is still in progress has no end time yet — say so rather than
		// rendering a blank or, worse, an age computed from nothing.
		expect(lastRunAge(undefined, now)).toBe('—');
	});

	it('is an em dash for an unparseable timestamp', () => {
		expect(lastRunAge('not-a-date', now)).toBe('—');
	});
});

describe('descriptorOriginLabel', () => {
	it('names where the descriptor came from', () => {
		expect(descriptorOriginLabel({ origin: 'repo', overridden: false })).toBe('from the repository');
		expect(descriptorOriginLabel({ origin: 'live', overridden: false })).toBe(
			"from this server's descriptor file"
		);
	});

	it('calls out an override explicitly', () => {
		expect(descriptorOriginLabel({ origin: 'stored', overridden: true })).toBe(
			'overriding the repository'
		);
	});
});

describe('descriptorCheckMessage', () => {
	it('summarises a descriptor that was found', () => {
		expect(descriptorCheckMessage({ ok: true, pipeline: 'spektr-ci', stages: 3 })).toEqual({
			tone: 'ok',
			text: "found kontinuance.yml — pipeline 'spektr-ci', 3 stages"
		});
	});

	it('passes a failure through as a warning', () => {
		expect(descriptorCheckMessage({ ok: false, message: 'no kontinuance.yml on main' })).toEqual({
			tone: 'warn',
			text: 'no kontinuance.yml on main'
		});
	});

	it('is null when nothing was checked', () => {
		expect(descriptorCheckMessage(undefined)).toBeNull();
	});
});
