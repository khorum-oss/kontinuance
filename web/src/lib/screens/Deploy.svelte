<script lang="ts">
	import { color } from '$lib/theme/tokens';
	import type { Deploy } from '$lib/api/types';

	let {
		deploy = null,
		loading = false,
		error = null,
		onretry
	}: {
		deploy?: Deploy | null;
		loading?: boolean;
		error?: string | null;
		onretry?: () => void;
	} = $props();

	// Deploy state vocabulary → color (synced/healthy ok, progressing live, pending muted, failed red).
	function stateColor(state: string): string {
		const s = state.toLowerCase();
		if (s.startsWith('sync') || s.startsWith('health') || s.startsWith('publish') || s.startsWith('push'))
			return color.ok;
		if (s.startsWith('progress') || s.startsWith('running')) return color.teal;
		if (s.startsWith('fail') || s.startsWith('degrad')) return color.fail;
		return color.muted3;
	}
</script>

<div class="screen">
	{#if loading}
		<div class="note k-mono">loading deploy…</div>
	{:else if error}
		<div class="err">
			<div class="k-mono emsg">{error}</div>
			<button class="k-mono retry" onclick={() => onretry?.()}>RETRY</button>
		</div>
	{:else if deploy}
		<div class="nodes">
			{#each deploy.nodes as n, i (n.id)}
				{#if i > 0}<div class="conn"></div>{/if}
				<div class="node" style="border-color:{stateColor(n.status)}44;">
					<div class="node-head">
						<span class="dot" style="background:{stateColor(n.status)};"></span>
						<span class="k-mono nlabel">{n.label}</span>
						<span class="k-mono nstate" style="color:{stateColor(n.status)};">{n.status}</span>
					</div>
					<div class="ntitle">{n.title}</div>
					<div class="k-mono nmeta">{n.meta}</div>
				</div>
			{/each}
		</div>

		<div class="cards">
			<div class="card manifest">
				<div class="k-mono label">ARTIFACT MANIFEST</div>
				{#each deploy.artifacts as a (a.name)}
					<div class="artifact">
						<span class="k-mono kind" style="color:{stateColor(a.state)};">{a.kind}</span>
						<span class="k-mono aname">{a.name}</span>
						<span class="k-mono digest">{a.digest}</span>
						<span class="k-mono astate" style="color:{stateColor(a.state)};">{a.state}</span>
					</div>
				{/each}
			</div>
			<div class="card env">
				<div class="k-mono label">STAGE ENVIRONMENT</div>
				<div class="stats">
					<div class="stat">
						<div class="big" style="color:{stateColor(deploy.environment.health)};">
							{deploy.environment.podsReady}
						</div>
						<div class="k-mono slabel">PODS READY</div>
					</div>
					<div class="stat">
						<div class="big">{deploy.environment.syncRevision}</div>
						<div class="k-mono slabel">SYNC REVISION</div>
					</div>
					<div class="stat">
						<div class="big" style="color:{stateColor(deploy.environment.health)};">
							{deploy.environment.health}
						</div>
						<div class="k-mono slabel">APP HEALTH</div>
					</div>
				</div>
				<div class="k-mono emeta">{deploy.environment.meta}</div>
			</div>
		</div>
	{/if}
</div>

<style>
	.screen {
		padding: 26px;
		display: flex;
		flex-direction: column;
		gap: 22px;
	}
	.nodes {
		display: flex;
		align-items: stretch;
	}
	.conn {
		flex: none;
		width: 56px;
		align-self: center;
		height: 2px;
		background-image: linear-gradient(90deg, var(--k-muted-4) 55%, transparent 55%);
		background-size: 12px 2px;
		animation: kflow 0.9s linear infinite;
	}
	.node {
		flex: 1;
		min-width: 0;
		padding: 20px;
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.node-head {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
	}
	.nlabel {
		font-size: 10.5px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.nstate {
		margin-left: auto;
		font-size: 9.5px;
	}
	.ntitle {
		font-size: 17px;
		font-weight: 600;
		color: var(--k-heading);
	}
	.nmeta {
		font-size: 10.5px;
		color: var(--k-muted-3);
		line-height: 1.8;
		white-space: pre-line;
	}
	.cards {
		display: flex;
		gap: 22px;
	}
	.card {
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		padding: 18px 20px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.manifest {
		flex: 1.2;
	}
	.env {
		flex: 1;
	}
	.label {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.artifact {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 10px 12px;
		background: rgba(9, 14, 19, 0.7);
		border: 1px solid var(--k-border-row);
		border-radius: 5px;
	}
	.kind {
		flex: none;
		font-size: 8.5px;
		letter-spacing: 1px;
		padding: 2px 6px;
		border-radius: 3px;
		border: 1px solid currentColor;
	}
	.aname {
		font-size: 11.5px;
		color: var(--k-text);
	}
	.digest {
		margin-left: auto;
		font-size: 10px;
		color: var(--k-muted-4);
	}
	.astate {
		flex: none;
		font-size: 10px;
	}
	.stats {
		display: flex;
		gap: 24px;
	}
	.big {
		font-size: 26px;
		font-weight: 700;
		color: var(--k-text);
	}
	.slabel {
		font-size: 9.5px;
		letter-spacing: 1px;
		color: var(--k-muted-4);
	}
	.emeta {
		font-size: 10.5px;
		color: var(--k-muted-3);
		line-height: 1.9;
		white-space: pre-line;
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
