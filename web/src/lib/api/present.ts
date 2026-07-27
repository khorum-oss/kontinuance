// View helpers that turn a flat [RunRecord] into the fields the runs table and detail header show.
// Kept separate from the transport so components stay presentational.

import { normalizeStatus, type Status } from '../theme/tokens';
import type { RunRecord } from './types';

export interface RunView {
	id: string;
	status: Status;
	ref: string;
	message: string;
	progress: number; // 0..100
	indeterminate: boolean; // running → animated bar
	duration: string;
	age: string;
}

function shortSha(sha?: string): string {
	return sha ? sha.slice(0, 7) : '';
}

/** "repo · sha" (falling back to the trigger, then em dash). */
export function runRef(r: RunRecord): string {
	const parts = [r.repo, shortSha(r.sha)].filter(Boolean);
	if (parts.length) return parts.join(' · ');
	return r.trigger ?? '—';
}

/** The row's human label: pipeline name, or the failure reason for a failed run. */
export function runMessage(r: RunRecord): string {
	const status = normalizeStatus(r.status);
	if (status === 'failed' && (r.reason || r.failingStep)) {
		return r.failingStep ? `${r.failingStep}: ${r.reason ?? 'failed'}` : (r.reason ?? 'failed');
	}
	return r.pipeline || r.id;
}

function progressFor(status: Status): { progress: number; indeterminate: boolean } {
	switch (status) {
		case 'success':
		case 'failed':
		case 'timedout':
		case 'cancelled':
		case 'skipped':
			return { progress: 100, indeterminate: false };
		case 'running':
			return { progress: 66, indeterminate: true };
		case 'pending':
		default:
			return { progress: 0, indeterminate: false };
	}
}

function fmtDuration(ms: number): string {
	if (ms <= 0 || !Number.isFinite(ms)) return '—';
	const s = Math.round(ms / 1000);
	if (s < 60) return `${s}s`;
	const m = Math.floor(s / 60);
	const rem = s % 60;
	if (m < 60) return `${m}m ${rem.toString().padStart(2, '0')}s`;
	const h = Math.floor(m / 60);
	return `${h}h ${(m % 60).toString().padStart(2, '0')}m`;
}

function fmtAge(fromMs: number, nowMs: number): string {
	const s = Math.round((nowMs - fromMs) / 1000);
	if (!Number.isFinite(s) || s < 0) return '—';
	if (s < 60) return `${s}s`;
	const m = Math.floor(s / 60);
	if (m < 60) return `${m}m`;
	const h = Math.floor(m / 60);
	if (h < 24) return `${h}h`;
	return `${Math.floor(h / 24)}d`;
}

/** Sort key for newest-first ordering: latest activity (end, else start). */
export function runSortKey(r: RunRecord): string {
	return r.endedAt ?? r.startedAt ?? '';
}

// ----- runs list filtering (037) -----

/** The runs-list filter criteria: a free-text query plus status/trigger facets ("all" = no facet). */
export interface RunFilter {
	query: string;
	status: string; // 'all' | canonical Status
	trigger: string; // 'all' | 'manual' | 'push' | 'pull_request'
}

/** True when [r] matches every active criterion in [f] (status by canonical status; query a case-insensitive
 * substring over id/pipeline/repo/sha; a blank query matches all). Pure. */
export function matchesRunFilter(r: RunRecord, f: RunFilter): boolean {
	if (f.status !== 'all' && normalizeStatus(r.status) !== f.status) return false;
	if (f.trigger !== 'all' && (r.trigger ?? '').toLowerCase() !== f.trigger) return false;
	const q = f.query.trim().toLowerCase();
	if (q) {
		const hay = [r.id, r.pipeline, r.repo, r.sha].filter(Boolean).join(' ').toLowerCase();
		if (!hay.includes(q)) return false;
	}
	return true;
}

/** Narrow [records] to those matching [f], preserving order. Pure. */
export function filterRuns(records: RunRecord[], f: RunFilter): RunRecord[] {
	return records.filter((r) => matchesRunFilter(r, f));
}

/** Merge records by id (later wins) and return them newest-first. */
export function mergeNewestFirst(records: Iterable<RunRecord>): RunRecord[] {
	const byId = new Map<string, RunRecord>();
	for (const r of records) byId.set(r.id, r);
	return [...byId.values()].sort((a, b) => runSortKey(b).localeCompare(runSortKey(a)));
}

/** Project a record into its display view. [nowMs] lets callers/tests pin "now". */
export function toRunView(r: RunRecord, nowMs: number = Date.now()): RunView {
	const status = normalizeStatus(r.status);
	const { progress, indeterminate } = progressFor(status);
	const started = r.startedAt ? Date.parse(r.startedAt) : NaN;
	const ended = r.endedAt ? Date.parse(r.endedAt) : NaN;
	return {
		id: r.id,
		status,
		ref: runRef(r),
		message: runMessage(r),
		progress,
		indeterminate,
		duration: Number.isFinite(started) && Number.isFinite(ended) ? fmtDuration(ended - started) : '—',
		age: Number.isFinite(ended) ? fmtAge(ended, nowMs) : '—'
	};
}
