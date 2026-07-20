<script lang="ts">
	import { normalizeStatus, statusColor } from '$lib/theme/tokens';
	import type { Coverage, RunRecord } from '$lib/api/types';
	import CoverageBar from '$lib/components/CoverageBar.svelte';
	import LogLine from '$lib/components/LogLine.svelte';

	interface Line {
		time: string;
		message: string;
		tone: 'normal' | 'muted' | 'error' | 'ok';
	}

	let {
		run = null,
		logs = [],
		coverage = null,
		loading = false,
		error = null,
		notFound = false,
		deciding = false,
		decideError = null,
		onback,
		onretry,
		onapprove,
		onreject
	}: {
		run?: RunRecord | null;
		logs?: string[];
		coverage?: Coverage | null;
		loading?: boolean;
		error?: string | null;
		notFound?: boolean;
		deciding?: boolean;
		decideError?: string | null;
		onback?: () => void;
		onretry?: () => void;
		onapprove?: () => void;
		onreject?: () => void;
	} = $props();

	const status = $derived(run ? normalizeStatus(run.status) : 'pending');
	const active = $derived(status === 'running' || status === 'waiting' || status === 'pending');

	function hhmmss(iso?: string): string {
		if (!iso) return '';
		const d = new Date(iso);
		const p = (n: number) => n.toString().padStart(2, '0');
		return `${p(d.getUTCHours())}:${p(d.getUTCMinutes())}:${p(d.getUTCSeconds())}`;
	}

	// A short metadata summary from the run record, followed by the run's real recorded step output (018).
	// While the run is active the page re-fetches the log; an empty log shows an explicit state.
	const lines = $derived.by<Line[]>(() => {
		if (!run) return [];
		const out: Line[] = [];
		out.push({ time: hhmmss(run.startedAt), message: `run ${run.id} · pipeline ${run.pipeline}`, tone: 'normal' });
		if (run.repo || run.sha) {
			out.push({ time: hhmmss(run.startedAt), message: `ref ${[run.repo, run.sha].filter(Boolean).join(' · ')}`, tone: 'muted' });
		}
		if (run.trigger) out.push({ time: hhmmss(run.startedAt), message: `trigger ${run.trigger}`, tone: 'muted' });
		out.push({ time: hhmmss(run.endedAt), message: `status ${run.status}`, tone: status === 'failed' ? 'error' : status === 'success' ? 'ok' : 'normal' });
		if (status === 'failed' && (run.failingStep || run.reason)) {
			out.push({ time: hhmmss(run.endedAt), message: `${run.failingStep ?? 'failure'}: ${run.reason ?? ''}`.trim(), tone: 'error' });
		}
		out.push({ time: '', message: '── step output ──', tone: 'muted' });
		if (logs.length) {
			for (const line of logs) out.push({ time: '', message: line, tone: 'normal' });
		} else {
			out.push({
				time: '',
				message: active ? '— waiting for output… —' : '— no output recorded for this run —',
				tone: 'muted'
			});
		}
		return out;
	});
</script>

<div class="screen">
	<div class="head">
		<button class="k-mono back" onclick={() => onback?.()}>← RUNS</button>
		{#if run}
			<span class="name">{run.id}</span>
			<span class="k-mono badge" style="color:{statusColor(status)}; border-color:{statusColor(status)};">
				{run.status}
			</span>
			<div class="meta k-mono">
				<span>pipeline <span class="v">{run.pipeline}</span></span>
				{#if run.trigger}<span>trigger <span class="v">{run.trigger}</span></span>{/if}
				{#if run.sha}<span>sha <span class="v">{run.sha.slice(0, 7)}</span></span>{/if}
			</div>
		{/if}
	</div>

	{#if loading}
		<div class="note k-mono">loading run…</div>
	{:else if notFound}
		<div class="note k-mono">run not found</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if run}
		{#if status === 'waiting'}
			<div class="gate">
				<span class="k-mono gmsg">this run is paused for manual approval</span>
				<div class="gactions">
					<button class="k-mono approve" disabled={deciding} onclick={() => onapprove?.()}>
						{deciding ? 'WORKING…' : 'APPROVE'}
					</button>
					<button class="k-mono reject" disabled={deciding} onclick={() => onreject?.()}>REJECT</button>
				</div>
				{#if decideError}<span class="k-mono gerror">{decideError}</span>{/if}
			</div>
		{/if}
		<div class="body">
			<div class="logpanel">
				<div class="k-mono lhead">LOG STREAM</div>
				<div class="lines">
					{#each lines as l, i (i)}
						<LogLine time={l.time} message={l.message} tone={l.tone} />
					{/each}
				</div>
			</div>
			{#if coverage}
				<div class="cov">
					<div class="k-mono chead">COVERAGE</div>
					<div class="ctotal">{coverage.line.pct}</div>
					{#each coverage.modules.slice(0, 4) as m (m.name)}
						<div class="cmod">
							<div class="k-mono crow"><span>{m.name}</span><span class="cpct">{m.linePct}%</span></div>
							<CoverageBar pct={m.linePct} height={3} />
						</div>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.screen {
		height: 100%;
		display: flex;
		flex-direction: column;
		min-height: 0;
		padding: 20px 26px;
		gap: 14px;
	}
	.head {
		flex: none;
		display: flex;
		align-items: center;
		gap: 16px;
	}
	.back {
		font-size: 10.5px;
		letter-spacing: 1.5px;
		color: var(--k-teal);
		background: none;
		border: 1px solid rgba(94, 234, 212, 0.25);
		border-radius: 4px;
		padding: 6px 10px;
		cursor: pointer;
	}
	.back:hover {
		background: rgba(94, 234, 212, 0.08);
	}
	.name {
		font-size: 16px;
		font-weight: 600;
		color: var(--k-heading);
	}
	.badge {
		font-size: 10px;
		letter-spacing: 1px;
		padding: 3px 8px;
		border-radius: 3px;
		border: 1px solid;
	}
	.meta {
		margin-left: auto;
		display: flex;
		gap: 18px;
		font-size: 10.5px;
		color: var(--k-muted-3);
	}
	.v {
		color: var(--k-muted);
	}
	.gate {
		flex: none;
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 12px 16px;
		border: 1px solid rgba(251, 212, 107, 0.4);
		background: rgba(251, 212, 107, 0.06);
		border-radius: 6px;
	}
	.gmsg {
		font-size: 11px;
		color: var(--k-warn);
		letter-spacing: 0.5px;
	}
	.gactions {
		display: flex;
		gap: 10px;
	}
	.approve,
	.reject {
		font-size: 10px;
		letter-spacing: 1.5px;
		border-radius: 4px;
		padding: 7px 16px;
		background: none;
		cursor: pointer;
	}
	.approve {
		color: var(--k-ok);
		border: 1px solid rgba(52, 211, 153, 0.5);
	}
	.approve:hover:not(:disabled) {
		background: rgba(52, 211, 153, 0.1);
	}
	.reject {
		color: var(--k-fail);
		border: 1px solid rgba(248, 113, 113, 0.45);
	}
	.reject:hover:not(:disabled) {
		background: rgba(248, 113, 113, 0.1);
	}
	.approve:disabled,
	.reject:disabled {
		opacity: 0.55;
		cursor: default;
	}
	.gerror {
		font-size: 10px;
		color: var(--k-fail);
	}
	.body {
		flex: 1;
		min-height: 0;
		display: flex;
		gap: 14px;
	}
	.logpanel {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		background: rgba(7, 11, 15, 0.95);
		border: 1px solid var(--k-border);
		border-radius: 6px;
		overflow: hidden;
	}
	.lhead {
		flex: none;
		padding: 8px 14px;
		border-bottom: 1px solid var(--k-border-soft);
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.lines {
		flex: 1;
		overflow-y: auto;
		padding: 12px 16px;
	}
	.cov {
		flex: none;
		width: 264px;
		display: flex;
		flex-direction: column;
		gap: 12px;
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 6px;
		padding: 16px;
	}
	.chead {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.ctotal {
		font-size: 30px;
		font-weight: 700;
		color: var(--k-teal);
	}
	.cmod {
		display: flex;
		flex-direction: column;
		gap: 5px;
	}
	.crow {
		display: flex;
		justify-content: space-between;
		font-size: 10px;
		color: var(--k-muted);
	}
	.cpct {
		color: var(--k-muted-3);
	}
	.note {
		padding: 40px 16px;
		text-align: center;
		font-size: 11px;
		color: var(--k-muted-4);
	}
	.err {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 14px;
		padding: 40px 16px;
	}
	.emsg {
		font-size: 11px;
		color: var(--k-fail);
	}
	.retry {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-teal);
		border: 1px solid rgba(94, 234, 212, 0.4);
		background: none;
		border-radius: 4px;
		padding: 8px 18px;
		cursor: pointer;
	}
</style>
