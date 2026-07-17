<script lang="ts">
	import { normalizeStatus, statusColor, toolAccent, type Status } from '$lib/theme/tokens';
	import type { Pipeline, PipelineTask } from '$lib/api/types';
	import ProgressBar from '$lib/components/ProgressBar.svelte';
	import TaskCard from '$lib/components/TaskCard.svelte';

	let {
		pipeline = null,
		loading = false,
		error = null,
		onretry
	}: {
		pipeline?: Pipeline | null;
		loading?: boolean;
		error?: string | null;
		onretry?: () => void;
	} = $props();

	const allTasks = $derived<PipelineTask[]>(pipeline ? pipeline.stages.flatMap((s) => s.tasks) : []);

	const overall = $derived(
		allTasks.length ? Math.round(allTasks.reduce((a, t) => a + t.progress, 0) / allTasks.length) : 0
	);

	const runStatus = $derived<Status>(
		allTasks.some((t) => normalizeStatus(t.status) === 'failed')
			? 'failed'
			: allTasks.some((t) => normalizeStatus(t.status) === 'running')
				? 'running'
				: allTasks.length && allTasks.every((t) => normalizeStatus(t.status) === 'success')
					? 'success'
					: 'pending'
	);

	let hovered = $state<string | null>(null);

	// Related = the hovered task, its dependencies, and any task that depends on it.
	const related = $derived.by(() => {
		if (!hovered) return new Set<string>();
		const set = new Set<string>([hovered]);
		const h = allTasks.find((t) => t.id === hovered);
		h?.deps.forEach((d) => set.add(d));
		for (const t of allTasks) if (t.deps.includes(hovered)) set.add(t.id);
		return set;
	});

	function stageMark(tasks: PipelineTask[]): string {
		const done = tasks.filter((t) => normalizeStatus(t.status) === 'success').length;
		return `${done}/${tasks.length}`;
	}
</script>

<div class="screen">
	{#if loading}
		<div class="note k-mono">loading pipeline…</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if pipeline}
		<div class="header">
			<div class="status">
				<span class="dot" style="background:{statusColor(runStatus)};"></span>
				<span class="k-mono stext" style="color:{statusColor(runStatus)};">{runStatus.toUpperCase()}</span>
			</div>
			<div class="sep"></div>
			<div class="run">
				<span class="rtitle">pipeline run</span>
				<span class="k-mono rsub">{pipeline.runId} · {allTasks.length} tasks</span>
			</div>
			<div class="progress">
				<div class="k-mono plabel"><span>TOTAL PROGRESS</span><span class="teal">{overall}%</span></div>
				<ProgressBar value={overall} indeterminate={runStatus === 'running'} />
			</div>
			<button class="k-mono replay" onclick={() => onretry?.()}>↻ REPLAY</button>
		</div>

		<div class="flow">
			{#each pipeline.stages as stage, i (stage.id)}
				{#if i > 0}<div class="conn"></div>{/if}
				<div class="stage">
					<div class="shead k-mono">
						<span class="idx">{String(i + 1).padStart(2, '0')}</span>
						<span class="sname">{stage.name}</span>
						<span class="mark">{stageMark(stage.tasks)}</span>
					</div>
					{#each stage.tasks as task (task.id)}
						<TaskCard
							{task}
							active={hovered === task.id}
							related={hovered !== null && hovered !== task.id && related.has(task.id)}
							dim={hovered !== null && !related.has(task.id)}
							onhover={(id) => (hovered = id)}
						/>
					{/each}
				</div>
			{/each}
		</div>

		<div class="feed">
			<div class="fhead k-mono">
				<span class="blink"></span>
				<span>TELEMETRY // pipeline</span>
				<span class="hint">hover a task to trace its dependencies</span>
			</div>
			<div class="flines k-mono">
				{#each allTasks as t (t.id)}
					<div class="fline">
						<span class="ftool" style="color:{toolAccent(t.tool)};">{t.tool}</span>
						<span class="fname">{t.name}</span>
						<span class="fstatus" style="color:{statusColor(normalizeStatus(t.status))};">{t.status}</span>
					</div>
				{/each}
			</div>
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
		gap: 16px;
	}
	.header {
		flex: none;
		display: flex;
		align-items: center;
		gap: 20px;
		padding: 16px 20px;
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 6px;
	}
	.status {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.dot {
		width: 9px;
		height: 9px;
		border-radius: 50%;
	}
	.stext {
		font-size: 12px;
		letter-spacing: 1.5px;
	}
	.sep {
		width: 1px;
		height: 24px;
		background: var(--k-border);
	}
	.run {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.rtitle {
		font-size: 13.5px;
		font-weight: 600;
		color: var(--k-heading);
	}
	.rsub {
		font-size: 10.5px;
		color: var(--k-muted-3);
	}
	.progress {
		flex: 1;
		max-width: 340px;
		margin-left: auto;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.plabel {
		display: flex;
		justify-content: space-between;
		font-size: 10px;
		color: var(--k-muted-3);
	}
	.teal {
		color: var(--k-teal);
	}
	.replay {
		flex: none;
		font-size: 10.5px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
		border: 1px solid var(--k-border);
		border-radius: 4px;
		padding: 9px 16px;
		background: none;
		cursor: pointer;
	}
	.replay:hover {
		background: rgba(94, 234, 212, 0.08);
		color: var(--k-teal);
	}
	.flow {
		flex: 1;
		min-height: 0;
		overflow: auto;
		display: flex;
		align-items: flex-start;
		min-width: max-content;
		padding: 6px 2px;
	}
	.conn {
		flex: none;
		width: 30px;
		margin-top: 52px;
		height: 2px;
		background-image: linear-gradient(90deg, var(--k-muted-4) 55%, transparent 55%);
		background-size: 12px 2px;
		animation: kflow 1s linear infinite;
	}
	.stage {
		flex: none;
		width: 212px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.shead {
		display: flex;
		align-items: baseline;
		gap: 8px;
		padding: 0 4px;
	}
	.idx {
		font-size: 10px;
		color: var(--k-faint);
	}
	.sname {
		font-size: 11px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.mark {
		margin-left: auto;
		font-size: 10px;
		color: var(--k-muted-3);
	}
	.feed {
		flex: none;
		height: 150px;
		display: flex;
		flex-direction: column;
		background: var(--k-surface-2);
		border: 1px solid var(--k-border);
		border-radius: 6px;
		overflow: hidden;
	}
	.fhead {
		flex: none;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 8px 14px;
		border-bottom: 1px solid var(--k-border-soft);
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.blink {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: var(--k-teal);
		animation: kblink 1.2s ease-in-out infinite;
	}
	.hint {
		margin-left: auto;
		letter-spacing: 1px;
		color: var(--k-faint);
		font-size: 9px;
	}
	.flines {
		flex: 1;
		overflow-y: auto;
		padding: 10px 14px;
		font-size: 11px;
		line-height: 1.8;
	}
	.fline {
		display: flex;
		gap: 12px;
	}
	.ftool {
		flex: none;
		width: 56px;
	}
	.fname {
		flex: 1;
		color: var(--k-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.fstatus {
		flex: none;
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
