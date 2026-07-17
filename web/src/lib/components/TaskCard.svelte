<script lang="ts">
	import { normalizeStatus, statusColor } from '$lib/theme/tokens';
	import type { PipelineTask } from '$lib/api/types';
	import ProgressBar from './ProgressBar.svelte';
	import ToolBadge from './ToolBadge.svelte';

	let {
		task,
		active = false,
		related = false,
		dim = false,
		onhover
	}: {
		task: PipelineTask;
		active?: boolean;
		related?: boolean;
		dim?: boolean;
		onhover?: (id: string | null) => void;
	} = $props();

	const status = $derived(normalizeStatus(task.status));
	const c = $derived(statusColor(status));
</script>

<div
	class="card"
	class:active
	class:related
	class:dim
	role="group"
	onmouseenter={() => onhover?.(task.id)}
	onmouseleave={() => onhover?.(null)}
>
	<div class="top">
		<span class="name">{task.name}</span>
		<ToolBadge tool={task.tool} />
	</div>
	<div class="bar"><ProgressBar value={task.progress} indeterminate={status === 'running'} fill={c} /></div>
	<div class="foot k-mono">
		<span style="color:{c};">{task.status}</span>
		{#if task.deps.length}
			<span class="dep">⇢ {task.deps.length} dep{task.deps.length > 1 ? 's' : ''}</span>
		{/if}
		<span class="prog">{task.progress}%</span>
	</div>
</div>

<style>
	.card {
		padding: 12px 14px;
		border-radius: 6px;
		background: rgba(13, 20, 28, 0.85);
		border: 1px solid var(--k-border);
		transition:
			opacity 0.2s,
			border-color 0.2s;
	}
	.card:hover,
	.card.active {
		border-color: var(--k-teal);
	}
	.card.related {
		border-color: rgba(94, 234, 212, 0.5);
	}
	.card.dim {
		opacity: 0.35;
	}
	.top {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.name {
		font-size: 12.5px;
		font-weight: 500;
		color: var(--k-text);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.top :global(.badge) {
		margin-left: auto;
	}
	.bar {
		margin: 10px 0 8px;
	}
	.foot {
		display: flex;
		justify-content: space-between;
		gap: 6px;
		font-size: 9.5px;
	}
	.dep {
		color: var(--k-teal);
	}
	.prog {
		color: var(--k-muted-4);
	}
</style>
