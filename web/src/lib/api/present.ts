// View helpers that turn a flat [RunRecord] into the fields the runs table and detail header show.
// Kept separate from the transport so components stay presentational.

import { normalizeStatus, type Status } from '../theme/tokens';
import type { DescriptorCheck, RunRecord } from './types';

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

/**
 * How long ago a project's most recent run finished, for the entry picker. Absent or unparseable input
 * renders an em dash rather than a blank or a nonsense age — a project whose newest run is still in
 * progress legitimately has no end time yet. [nowMs] lets callers/tests pin "now". Pure.
 */
export function lastRunAge(lastRunAt: string | undefined, nowMs: number = Date.now()): string {
	if (!lastRunAt) return '—';
	const at = Date.parse(lastRunAt);
	return Number.isFinite(at) ? fmtAge(at, nowMs) : '—';
}

/** Sort key for newest-first ordering: latest activity (end, else start). */
export function runSortKey(r: RunRecord): string {
	return r.endedAt ?? r.startedAt ?? '';
}

// ----- what a run actually executed -----

function plural(n: number, noun: string): string {
	return `${n} ${noun}${n === 1 ? '' : 's'}`;
}

/**
 * A one-line roll-up of the work a run performed, e.g. "2 stages · 3 steps". Empty when the record
 * carries no stage roll-up — older records predate it, and the server omits the array when empty —
 * so the caller can drop the line rather than render a misleading "0 stages". Pure.
 */
export function runWorkSummary(r: RunRecord): string {
	const stages = r.stages ?? [];
	if (!stages.length) return '';
	const steps = stages.reduce((n, s) => n + (s.steps?.length ?? 0), 0);
	return `${plural(stages.length, 'stage')} · ${plural(steps, 'step')}`;
}

/**
 * Explains a run whose only stage was the checkout synthesized from the project source (033) — the
 * signature of a descriptor that declared no stages of its own. Such a run clones the repository,
 * finds nothing to do and reports Success, which reads far more like a run that stalled than one
 * that finished.
 *
 * The parser now rejects a stage-less descriptor outright, so this is here for runs already on
 * disk. It matches the exact shape `ProjectSourceInjector` prepends: a lone stage named `checkout`
 * holding a single `git` step of the same name. A hand-written pipeline could in principle collide
 * with that shape, in which case the note is still true — only a source checkout did run. Pure.
 */
export function sourceCheckoutOnlyNote(r: RunRecord): string | null {
	const stages = r.stages ?? [];
	if (stages.length !== 1) return null;
	const [stage] = stages;
	if (stage.name !== 'checkout' || stage.steps?.length !== 1) return null;
	const [step] = stage.steps;
	if (step.name !== 'checkout' || step.tool !== 'git') return null;
	return 'this pipeline declared no stages — only the source checkout ran';
}

// ----- runs list filtering (037) -----

/** The runs-list filter criteria: a free-text query plus status/trigger/project facets ("all" = no facet).
 * `project` is optional — callers that don't scope by project (e.g. the run stream merge point before
 * this feature is wired in) can omit it entirely; that is equivalent to `'all'`. */
export interface RunFilter {
	query: string;
	status: string; // 'all' | canonical Status
	trigger: string; // 'all' | 'manual' | 'push' | 'pull_request'
	project?: string; // 'all' | project name | undefined (== 'all')
}

/** The project a run belongs to — the browser mirror of the server's resolution rule (039):
 * the explicit project, else the repository's short name, else none. Pure. */
export function runProject(r: RunRecord): string | null {
	const explicit = r.project?.trim();
	if (explicit) return explicit;
	const fromRepo = r.repo?.split('/').pop()?.trim();
	return fromRepo || null;
}

/** True when [r] matches every active criterion in [f] (status by canonical status; query a case-insensitive
 * substring over id/pipeline/repo/sha; a blank query matches all). Pure. */
export function matchesRunFilter(r: RunRecord, f: RunFilter): boolean {
	if (f.status !== 'all' && normalizeStatus(r.status) !== f.status) return false;
	if (f.trigger !== 'all' && (r.trigger ?? '').toLowerCase() !== f.trigger) return false;
	const project = f.project ?? 'all';
	if (project !== 'all' && runProject(r) !== project) return false;
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

/**
 * A fingerprint of everything a viewer would see change about [r]: its status and, when the server has
 * recorded one, each step's status within each stage.
 *
 * The pipeline view follows a run while it executes, and a run's `status` stays `Running` from the first
 * step to the last — so keying a refresh on status alone means a build's steps never appear to advance.
 * Pure.
 */
export function runFingerprint(r: RunRecord): string {
	const stages = (r.stages ?? [])
		.map((s) => `${s.name}:${s.status}:${(s.steps ?? []).map((t) => `${t.name}=${t.status}`).join(',')}`)
		.join('|');
	return `${r.id}:${r.status}:${stages}`;
}

/** Merge records by id (later wins) and return them newest-first. */
export function mergeNewestFirst(records: Iterable<RunRecord>): RunRecord[] {
	const byId = new Map<string, RunRecord>();
	for (const r of records) byId.set(r.id, r);
	return [...byId.values()].sort((a, b) => runSortKey(b).localeCompare(runSortKey(a)));
}

// ----- descriptor provenance (041) -----

/** Where the descriptor the server would run came from, in words. Pure. */
export function descriptorOriginLabel(config: { origin?: string; overridden?: boolean }): string {
	if (config.overridden) return 'overriding the repository';
	switch (config.origin) {
		case 'repo':
			return 'from the repository';
		case 'stored':
			return 'stored on this server';
		default:
			return "from this server's descriptor file";
	}
}

/** The add-time descriptor check as a tone + line, or null when nothing was checked. Pure. */
export function descriptorCheckMessage(
	check: DescriptorCheck | undefined
): { tone: 'ok' | 'warn'; text: string } | null {
	if (!check) return null;
	if (check.ok) {
		const stages = check.stages ?? 0;
		return {
			tone: 'ok',
			text: `found kontinuance.yml — pipeline '${check.pipeline ?? 'the descriptor'}', ${stages} stage${stages === 1 ? '' : 's'}`
		};
	}
	return { tone: 'warn', text: check.message ?? 'the descriptor could not be read' };
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
