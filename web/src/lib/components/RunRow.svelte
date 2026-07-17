<script lang="ts">
	import type { RunView } from '$lib/api/present';
	import { statusColor } from '$lib/theme/tokens';
	import ProgressBar from './ProgressBar.svelte';
	import StatusDot from './StatusDot.svelte';

	let { run, onopen }: { run: RunView; onopen?: (id: string) => void } = $props();
</script>

<div
	class="row"
	role="button"
	tabindex="0"
	onclick={() => onopen?.(run.id)}
	onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && onopen?.(run.id)}
>
	<StatusDot status={run.status} />
	<span class="id k-mono">{run.id}</span>
	<span class="ref k-mono">{run.ref}</span>
	<span class="msg">{run.message}</span>
	<div class="prog">
		<ProgressBar value={run.progress} indeterminate={run.indeterminate} fill={statusColor(run.status)} />
	</div>
	<span class="dur k-mono">{run.duration}</span>
	<span class="age k-mono">{run.age}</span>
</div>

<style>
	.row {
		display: grid;
		grid-template-columns: 14px 96px 1fr 1.4fr 120px 80px 60px;
		gap: 16px;
		align-items: center;
		padding: 13px 16px;
		background: rgba(13, 20, 28, 0.7);
		border: 1px solid var(--k-border-row);
		border-radius: 6px;
		cursor: pointer;
		transition:
			border-color 0.15s,
			background 0.15s;
	}
	.row:hover,
	.row:focus-visible {
		border-color: rgba(94, 234, 212, 0.4);
		background: rgba(15, 24, 33, 0.9);
		outline: none;
	}
	.id {
		font-size: 11.5px;
		color: var(--k-heading);
	}
	.ref {
		font-size: 11px;
		color: var(--k-muted-2);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.msg {
		font-size: 12.5px;
		color: var(--k-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.dur {
		font-size: 10.5px;
		color: var(--k-muted-3);
	}
	.age {
		font-size: 10.5px;
		color: var(--k-muted-4);
	}
</style>
