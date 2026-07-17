<script lang="ts">
	import type { Config } from '$lib/api/types';

	let {
		config = null,
		loading = false,
		error = null,
		onretry
	}: {
		config?: Config | null;
		loading?: boolean;
		error?: string | null;
		onretry?: () => void;
	} = $props();

	const lines = $derived(config ? config.text.split('\n') : []);

	function isComment(line: string): boolean {
		return line.trimStart().startsWith('#');
	}
</script>

<div class="screen">
	{#if loading}
		<div class="note k-mono">loading config…</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if config}
		<div class="src">
			<div class="src-head k-mono">
				<span class="fname">{config.source}</span>
				<span class="origin">SOURCE // repo root</span>
			</div>
			<div class="code k-mono">
				{#each lines as line, i (i)}
					<div class="line">
						<span class="ln">{i + 1}</span>
						{#if isComment(line)}
							<span class="comment">{line}</span>
						{:else if line.includes(':')}
							<span class="key">{line.slice(0, line.indexOf(':') + 1)}</span><span class="val"
								>{line.slice(line.indexOf(':') + 1)}</span
							>
						{:else}
							<span class="val">{line}</span>
						{/if}
					</div>
				{/each}
			</div>
		</div>

		<div class="side">
			<div class="card">
				<div class="k-mono label">RESOLVED PLAN</div>
				<div class="plan">
					{config.plan.stages} stages · {config.plan.tasks} tasks · max parallelism
					<span class="hl k-mono">{config.plan.maxParallel} lanes</span>
				</div>
				<div class="k-mono meta">
					toolchain {config.plan.toolchain}<br />publish → {config.plan.publish}<br />deploy → {config
						.plan.deploy}
				</div>
			</div>
			<div class="card dsl">
				<div class="dsl-head">
					<span class="k-mono label">KONTINUANCE DSL</span>
					<span class="k-mono roadmap">ROADMAP</span>
				</div>
				<div class="k-mono dsl-code">
					pipeline "kontinuance" {'{'}<br />&nbsp;&nbsp;stage(build) {'{'} parallel {'{'} … {'}'} {'}'}<br
					/>{'}'}
				</div>
				<div class="dsl-note">yml remains supported after the DSL lands.</div>
			</div>
		</div>
	{/if}
</div>

<style>
	.screen {
		padding: 24px 26px;
		display: flex;
		gap: 20px;
		align-items: flex-start;
	}
	.src {
		flex: 1.4;
		min-width: 0;
		background: rgba(7, 11, 15, 0.95);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		overflow: hidden;
	}
	.src-head {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 16px;
		border-bottom: 1px solid var(--k-border-soft);
	}
	.fname {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.origin {
		margin-left: auto;
		font-size: 9.5px;
		letter-spacing: 1px;
		color: var(--k-faint);
	}
	.code {
		padding: 16px 0;
		font-size: 12px;
		line-height: 1.85;
	}
	.line {
		display: flex;
	}
	.ln {
		flex: none;
		width: 44px;
		text-align: right;
		padding-right: 16px;
		color: var(--k-faint-2);
		user-select: none;
	}
	.comment {
		color: var(--k-faint);
	}
	.key {
		color: var(--k-muted-2);
		white-space: pre;
	}
	.val {
		color: var(--k-teal);
		white-space: pre;
	}
	.side {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.card {
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		padding: 18px 20px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.label {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.plan {
		font-size: 13px;
		color: var(--k-muted);
		line-height: 1.7;
	}
	.hl {
		color: var(--k-teal);
	}
	.meta {
		font-size: 10.5px;
		color: var(--k-muted-3);
		line-height: 1.9;
	}
	.dsl {
		border-style: dashed;
		border-color: var(--k-faint-2);
		opacity: 0.85;
	}
	.dsl-head {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.roadmap {
		font-size: 8.5px;
		letter-spacing: 1px;
		padding: 2px 6px;
		border-radius: 3px;
		color: var(--k-warn);
		border: 1px solid rgba(251, 212, 107, 0.4);
	}
	.dsl-code {
		font-size: 11px;
		color: var(--k-muted-4);
		line-height: 1.8;
	}
	.dsl-note {
		font-size: 12px;
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
		width: 100%;
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
