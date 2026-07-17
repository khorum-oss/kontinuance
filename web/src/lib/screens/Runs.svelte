<script lang="ts">
	import type { RunView } from '$lib/api/present';
	import RunRow from '$lib/components/RunRow.svelte';

	let {
		runs = [],
		loading = false,
		error = null,
		degraded = false,
		onopen,
		onretry
	}: {
		runs?: RunView[];
		loading?: boolean;
		error?: string | null;
		degraded?: boolean;
		onopen?: (id: string) => void;
		onretry?: () => void;
	} = $props();
</script>

<div class="screen">
	{#if degraded}
		<div class="degraded k-mono">live stream disconnected — showing last known state, retrying…</div>
	{/if}

	<div class="head k-mono">
		<span></span><span>RUN</span><span>REF</span><span>COMMIT</span><span>PROGRESS</span><span>TIME</span
		><span>AGE</span>
	</div>

	{#if loading}
		<div class="note k-mono">loading runs…</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if runs.length === 0}
		<div class="note k-mono">no runs recorded yet</div>
	{:else}
		<div class="rows">
			{#each runs as r (r.id)}
				<RunRow run={r} {onopen} />
			{/each}
		</div>
	{/if}
</div>

<style>
	.screen {
		padding: 22px 26px;
	}
	.head {
		display: grid;
		grid-template-columns: 14px 96px 1fr 1.4fr 120px 80px 60px;
		gap: 16px;
		align-items: center;
		padding: 0 16px 10px;
		font-size: 9.5px;
		letter-spacing: 1.5px;
		color: var(--k-faint);
	}
	.rows {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.note {
		padding: 40px 16px;
		text-align: center;
		font-size: 11px;
		color: var(--k-muted-4);
	}
	.degraded {
		margin-bottom: 12px;
		padding: 8px 14px;
		border: 1px solid rgba(251, 212, 107, 0.35);
		border-radius: 5px;
		font-size: 10px;
		color: var(--k-warn);
		background: rgba(251, 212, 107, 0.05);
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
	.retry:hover {
		background: rgba(94, 234, 212, 0.08);
	}
</style>
