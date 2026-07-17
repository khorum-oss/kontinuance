<script lang="ts">
	import CoverageBar from '$lib/components/CoverageBar.svelte';
	import { coverageColor } from '$lib/theme/tokens';
	import type { Coverage } from '$lib/api/types';

	let {
		coverage = null,
		loading = false,
		error = null,
		onretry
	}: {
		coverage?: Coverage | null;
		loading?: boolean;
		error?: string | null;
		onretry?: () => void;
	} = $props();

	let selected = $state<string | null>(null);
</script>

<div class="screen">
	{#if loading}
		<div class="note k-mono">loading coverage…</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if coverage}
		<div class="summary">
			<div class="card">
				<div class="k-mono label">LINE COVERAGE</div>
				<div class="big" style="color:{coverageColor(parseFloat(coverage.line.pct))};">
					{coverage.line.pct}
				</div>
				<div class="k-mono det">{coverage.line.covered} / {coverage.line.total} lines</div>
			</div>
			<div class="card">
				<div class="k-mono label">BRANCH COVERAGE</div>
				<div class="big" style="color:{coverageColor(parseFloat(coverage.branch.pct))};">
					{coverage.branch.pct}
				</div>
				<div class="k-mono det">{coverage.branch.covered} / {coverage.branch.total} branches</div>
			</div>
			<div class="card">
				<div class="k-mono label">CLASSES</div>
				<div class="big" style="color:var(--k-text);">{coverage.classes}</div>
				<div class="k-mono det">report {coverage.tool}</div>
			</div>
		</div>

		<div class="crumb k-mono">
			<button class="root" class:active={!selected} onclick={() => (selected = null)}>ALL MODULES</button>
			{#if selected}
				<span class="sep">/</span><span class="cur">{selected}</span>
			{/if}
			<span class="thresh">THRESHOLD 80% LINE · 60% BRANCH</span>
		</div>

		{#if selected}
			<div class="note k-mono">class-level breakdown for “{selected}” arrives with real Kover data</div>
		{:else}
			<div class="table">
				<div class="thead k-mono">
					<span>MODULE</span><span></span><span>LINES</span><span></span><span>BRANCHES</span><span
					></span><span class="r">MISSED</span>
				</div>
				{#each coverage.modules as m (m.name)}
					<button class="trow" onclick={() => (selected = m.name)}>
						<span class="name k-mono">{m.name}</span>
						<span class="kind k-mono" style="color:var(--k-muted-3);">{m.kind}</span>
						<CoverageBar pct={m.linePct} />
						<span class="pct k-mono" style="color:{coverageColor(m.linePct)};">{m.linePct}%</span>
						<CoverageBar pct={m.branchPct} />
						<span class="pct k-mono" style="color:{coverageColor(m.branchPct)};">{m.branchPct}%</span>
						<span class="missed k-mono">{m.missed}</span>
					</button>
				{/each}
			</div>
		{/if}
	{/if}
</div>

<style>
	.screen {
		padding: 22px 26px;
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.summary {
		display: flex;
		gap: 16px;
	}
	.card {
		flex: 1;
		padding: 18px 20px;
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.label {
		font-size: 9.5px;
		letter-spacing: 2px;
		color: var(--k-muted-4);
	}
	.big {
		font-size: 32px;
		font-weight: 700;
	}
	.det {
		font-size: 10.5px;
		color: var(--k-muted-4);
	}
	.crumb {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 11px;
	}
	.root {
		background: none;
		border: none;
		color: var(--k-muted-2);
		letter-spacing: 1px;
		cursor: pointer;
		padding: 0;
	}
	.root.active {
		color: var(--k-teal);
	}
	.sep {
		color: var(--k-faint);
	}
	.cur {
		color: var(--k-heading);
		letter-spacing: 1px;
	}
	.thresh {
		margin-left: auto;
		font-size: 9.5px;
		letter-spacing: 1px;
		color: var(--k-faint);
	}
	.table {
		background: var(--k-surface-2);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		overflow: hidden;
	}
	.thead,
	.trow {
		display: grid;
		grid-template-columns: minmax(140px, 1.3fr) 62px minmax(90px, 1fr) 56px minmax(90px, 1fr) 56px 60px;
		gap: 12px;
		align-items: center;
		padding: 12px 18px;
	}
	.thead {
		border-bottom: 1px solid var(--k-border-soft);
		font-size: 9.5px;
		letter-spacing: 1.5px;
		color: var(--k-faint);
	}
	.trow {
		width: 100%;
		background: none;
		border: none;
		border-bottom: 1px solid #0f1822;
		cursor: pointer;
		text-align: left;
	}
	.trow:hover {
		background: rgba(94, 234, 212, 0.04);
	}
	.name {
		font-size: 12px;
		color: var(--k-text);
	}
	.kind {
		font-size: 8.5px;
		letter-spacing: 1px;
	}
	.pct {
		font-size: 11px;
	}
	.missed {
		font-size: 11px;
		color: var(--k-muted-3);
	}
	.r {
		text-align: right;
	}
	.note {
		padding: 30px 16px;
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
