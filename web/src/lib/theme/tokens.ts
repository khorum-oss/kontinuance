// Typed design tokens mirroring app.css, for component logic (status → color, tool → color) and stories.

export const color = {
	bg: '#070b10',
	surface: 'rgba(13,20,28,0.8)',
	surface2: 'rgba(9,14,19,0.9)',
	inset: '#101a23',
	border: '#17222c',
	borderSoft: '#131e28',
	borderRow: '#141f29',
	text: '#dce8e6',
	heading: '#eafffa',
	muted: '#9fb6b0',
	muted2: '#7e9a93',
	muted3: '#5c7a72',
	muted4: '#4b6a63',
	faint: '#33484f',
	faint2: '#22313d',
	teal: '#5eead4',
	tealBright: '#8ff5e4',
	teal2: '#2dd4bf',
	ok: '#34d399',
	fail: '#f87171',
	warn: '#fbd46b',
	purple: '#c4b5fd'
} as const;

/** Canonical run/task status vocabulary the UI renders. */
export type Status = 'success' | 'failed' | 'running' | 'pending' | 'skipped' | 'timedout' | 'cancelled';

/** Map an engine status string (e.g. "Success", "Failed(step, …)") to a canonical [Status]. */
export function normalizeStatus(raw: string | null | undefined): Status {
	const s = (raw ?? '').toLowerCase();
	if (s.startsWith('success')) return 'success';
	if (s.startsWith('fail')) return 'failed';
	if (s.startsWith('run')) return 'running';
	if (s.startsWith('pend') || s.startsWith('queue') || s.startsWith('wait')) return 'pending';
	if (s.startsWith('skip')) return 'skipped';
	if (s.startsWith('timed')) return 'timedout';
	if (s.startsWith('cancel')) return 'cancelled';
	return 'pending';
}

export function statusColor(status: Status): string {
	switch (status) {
		case 'success':
			return color.ok;
		case 'failed':
		case 'timedout':
			return color.fail;
		case 'running':
			return color.teal;
		case 'cancelled':
			return color.muted4;
		case 'skipped':
			return color.faint;
		case 'pending':
		default:
			return color.muted3;
	}
}

/** Whether a status should visually pulse (in-flight). */
export function statusPulses(status: Status): boolean {
	return status === 'running';
}

/** Coverage color by threshold: healthy at/above target, warning in the band below, poor beneath. */
export function coverageColor(pct: number): string {
	if (pct >= 80) return color.teal;
	if (pct >= 60) return color.warn;
	return color.fail;
}

/** Per-tool accent colors used by task/tool badges. */
export const toolColor: Record<string, string> = {
	git: '#f0a36b',
	gradle: '#5eead4',
	maven: '#fb923c',
	env: '#94a3b8',
	cache: '#94a3b8',
	lint: '#c4b5fd',
	bun: '#fbd46b',
	oci: '#7dd3fc',
	nexus: '#7dd3fc',
	argo: '#fb8fa5'
};

export function toolAccent(tool: string): string {
	return toolColor[tool] ?? color.muted2;
}
