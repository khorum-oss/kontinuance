<script lang="ts">
	import StatusDot from '$lib/components/StatusDot.svelte';
	import { normalizeStatus } from '$lib/theme/tokens';
	import type { RunRecord, SourceStatus } from '$lib/api/types';

	let {
		source = null,
		runs = [],
		loading = false,
		error = null,
		onretry
	}: {
		source?: SourceStatus | null;
		runs?: RunRecord[];
		loading?: boolean;
		error?: string | null;
		onretry?: () => void;
	} = $props();

	const shortSha = (sha?: string) => (sha ? sha.slice(0, 9) : '—');
	function formatAge(seconds: number): string {
		if (seconds < 60) return `${seconds}s`;
		if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
		return `${Math.floor(seconds / 3600)}h`;
	}
</script>

<div class="screen">
	{#if loading}
		<div class="note k-mono">loading event source…</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if source && !source.configured}
		<div class="note k-mono unset">
			<div class="uhead">NO GITHUB EVENT SOURCE CONFIGURED</div>
			<div class="usub">
				The event source runs as the <code>kontinuance-ci</code> CLI. Point the server at its config with
				<code>kontinuance.github.config</code> to watch it here (read-only).
			</div>
		</div>
	{:else if source}
		<div class="head">
			<span class="k-mono title">GITHUB EVENT SOURCE</span>
			{#if source.heartbeat}
				{@const hb = source.heartbeat}
				<span class="live" class:stale={hb.stale}>
					<span class="live-dot"></span>
					<span class="k-mono">
						{hb.stale ? 'STALE' : 'POLLING'} · last checked {formatAge(hb.ageSeconds)} ago · {hb.cycles} cycles
					</span>
				</span>
			{:else}
				<span class="live unknown">
					<span class="live-dot"></span>
					<span class="k-mono">LIVENESS UNKNOWN · no heartbeat yet</span>
				</span>
			{/if}
			<span class="k-mono meta">
				polls every {source.pollIntervalSeconds}s · {source.baseUrl} · token env <code>{source.tokenEnv}</code>
			</span>
		</div>

		<!-- watched repositories -->
		<div class="section">
			<div class="k-mono sub">WATCHED REPOSITORIES</div>
			<div class="cards">
				{#each source.repositories ?? [] as r (r.slug)}
					<div class="repo">
						<div class="k-mono rslug">{r.slug}</div>
						<div class="k-mono rline">PR → <span class="p">{r.prPipeline}</span></div>
						<div class="k-mono rline">
							{#if r.pushPipeline}
								push <span class="b">{r.trackedBranch}</span> → <span class="p">{r.pushPipeline}</span>
							{:else}
								push delivery disabled
							{/if}
						</div>
					</div>
				{/each}
			</div>
		</div>

		<!-- poll cursors -->
		<div class="section">
			<div class="k-mono sub">POLL CURSORS</div>
			{#if (source.cursors ?? []).length === 0}
				<div class="note k-mono">no position recorded yet — the poller has not seen a commit</div>
			{:else}
				<div class="rows">
					{#each source.cursors ?? [] as c (c.key)}
						<div class="row">
							<span class="k-mono ckey">{c.key}</span>
							<span class="k-mono csha">{shortSha(c.sha)}</span>
						</div>
					{/each}
				</div>
			{/if}
		</div>

		<!-- github-triggered runs -->
		<div class="section">
			<div class="k-mono sub">GITHUB-TRIGGERED RUNS</div>
			{#if runs.length === 0}
				<div class="note k-mono">no runs from the event source yet</div>
			{:else}
				<div class="rows">
					{#each runs as run (run.id)}
						<div class="row">
							<span class="run-left">
								<StatusDot status={normalizeStatus(run.status)} />
								<span class="k-mono rid">{run.id}</span>
								<span class="k-mono rrepo">{run.repo ?? '—'}</span>
							</span>
							<span class="run-right">
								<span class="k-mono kind">{run.trigger}</span>
								<span class="k-mono rsha">{shortSha(run.sha)}</span>
							</span>
						</div>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</div>

<style>
	.screen {
		padding: 26px 30px;
		overflow-y: auto;
	}
	.note {
		padding: 40px 16px;
		text-align: center;
		font-size: 11px;
		color: var(--k-muted-4);
	}
	.unset {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.uhead {
		font-size: 12px;
		letter-spacing: 2px;
		color: var(--k-faint);
	}
	.usub {
		font-size: 11px;
		line-height: 1.8;
		color: var(--k-muted-4);
		max-width: 620px;
		margin: 0 auto;
	}
	code {
		color: var(--k-teal);
	}
	.err {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 14px;
		padding: 60px 16px;
	}
	.emsg {
		font-size: 11px;
		color: var(--k-fail);
	}
	.retry {
		padding: 8px 20px;
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-teal);
		border: 1px solid rgba(94, 234, 212, 0.45);
		background: none;
		border-radius: 5px;
		cursor: pointer;
	}
	.head {
		display: flex;
		align-items: baseline;
		gap: 16px;
		flex-wrap: wrap;
		margin-bottom: 22px;
	}
	.title {
		font-size: 13px;
		letter-spacing: 2.5px;
		color: var(--k-heading);
	}
	.meta {
		font-size: 10px;
		color: var(--k-faint);
	}
	.live {
		display: inline-flex;
		align-items: center;
		gap: 7px;
		font-size: 9.5px;
		letter-spacing: 1px;
		color: var(--k-ok);
	}
	.live.stale {
		color: var(--k-fail);
	}
	.live.unknown {
		color: var(--k-muted-4);
	}
	.live-dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: currentColor;
	}
	.live:not(.stale):not(.unknown) .live-dot {
		animation: kpulsesoft 3.6s ease-in-out infinite;
	}
	.section {
		margin-bottom: 26px;
	}
	.sub {
		font-size: 9.5px;
		letter-spacing: 2px;
		color: var(--k-faint);
		margin-bottom: 10px;
	}
	.cards {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
		gap: 12px;
	}
	.repo {
		border: 1px solid var(--k-border);
		background: var(--k-surface-2);
		border-radius: 6px;
		padding: 14px 16px;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.rslug {
		font-size: 12.5px;
		color: var(--k-text);
	}
	.rline {
		font-size: 10px;
		color: var(--k-muted-4);
	}
	.rline .p {
		color: var(--k-muted-2);
	}
	.rline .b {
		color: var(--k-teal);
	}
	.rows {
		display: flex;
		flex-direction: column;
		border: 1px solid var(--k-border);
		border-radius: 6px;
		overflow: hidden;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 11px 16px;
		background: var(--k-surface-2);
		border-bottom: 1px solid var(--k-border-soft);
	}
	.row:last-child {
		border-bottom: none;
	}
	.ckey {
		font-size: 11px;
		color: var(--k-text);
	}
	.csha,
	.rsha {
		font-size: 10.5px;
		color: var(--k-muted-3);
	}
	.run-left {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}
	.run-right {
		display: flex;
		align-items: center;
		gap: 14px;
		flex: none;
	}
	.rid {
		font-size: 11px;
		color: var(--k-text);
	}
	.rrepo {
		font-size: 10px;
		color: var(--k-muted-4);
	}
	.kind {
		font-size: 8.5px;
		letter-spacing: 1px;
		padding: 3px 7px;
		border-radius: 3px;
		color: var(--k-muted-2);
		border: 1px solid var(--k-border);
	}
</style>
