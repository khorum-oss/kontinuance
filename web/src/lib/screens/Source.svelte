<script lang="ts">
	import StatusDot from '$lib/components/StatusDot.svelte';
	import { normalizeStatus } from '$lib/theme/tokens';
	import type { ConnectSourceRequest, RunRecord, SourceStatus } from '$lib/api/types';

	let {
		source = null,
		runs = [],
		loading = false,
		error = null,
		busy = false,
		connectError = null,
		onretry,
		onconnect,
		ondisconnect
	}: {
		source?: SourceStatus | null;
		runs?: RunRecord[];
		loading?: boolean;
		error?: string | null;
		/** True while a connect/disconnect is in flight — the controls disable rather than queue. */
		busy?: boolean;
		/** A rejected connect/disconnect, shown against the form rather than replacing the screen. */
		connectError?: string | null;
		onretry?: () => void;
		onconnect?: (request: ConnectSourceRequest) => void;
		ondisconnect?: (forget: boolean) => void;
	} = $props();

	// The connect form. The token is held only until it is submitted; it is cleared on success and no read
	// ever returns it, so it never round-trips back into this component.
	let owner = $state('');
	let name = $state('');
	let prPipeline = $state('');
	let pushPipeline = $state('');
	let trackedBranch = $state('main');
	let token = $state('');
	let pollIntervalSeconds = $state(60);
	let showForm = $state(false);

	const canSubmit = $derived(
		owner.trim() !== '' && name.trim() !== '' && prPipeline.trim() !== '' && !busy
	);

	function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!canSubmit) return;
		onconnect?.({
			owner: owner.trim(),
			name: name.trim(),
			prPipeline: prPipeline.trim(),
			pushPipeline: pushPipeline.trim() || undefined,
			trackedBranch: trackedBranch.trim() || undefined,
			token: token.trim() || undefined,
			pollIntervalSeconds
		});
		token = '';
	}

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
		<div class="unset">
			<div class="k-mono uhead">NO GITHUB REPOSITORY CONNECTED</div>
			{#if source.manageable}
				<div class="k-mono usub">
					Connect a repository and Kontinuance polls it for pull requests and pushes, runs the pipeline
					you name, and reports the result back to GitHub as a commit status.
				</div>
				{@render connectForm()}
			{:else}
				<div class="k-mono usub">
					Connecting a repository from here needs operator authentication — set
					<code>KONTINUANCE_AUTH_USERNAME</code> and <code>KONTINUANCE_AUTH_PASSWORD</code> on the
					server, then reload. Without it this screen only displays a source started elsewhere (point
					<code>kontinuance.github.config</code> at its config).
				</div>
			{/if}
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
			{#if source.manageable}
				<span class="actions">
					{#if source.running}
						<button
							class="k-mono act"
							disabled={busy}
							onclick={() => ondisconnect?.(false)}
							aria-label="stop polling">STOP</button
						>
					{:else}
						<button
							class="k-mono act go"
							disabled={busy}
							onclick={() => showForm = !showForm}
							aria-label="reconnect the event source">RECONNECT</button
						>
					{/if}
					<button
						class="k-mono act danger"
						disabled={busy}
						onclick={() => ondisconnect?.(true)}
						aria-label="disconnect and forget">DISCONNECT</button
					>
				</span>
			{/if}
		</div>

		{#if source.manageable && !source.running}
			<div class="warn k-mono">
				NOT POLLING — the config is stored but no poll loop is running.
				{#if source.hasToken === false}
					No token is available; reconnect with one below.
				{/if}
			</div>
		{/if}

		{#if source.manageable && showForm}
			<div class="section">{@render connectForm()}</div>
		{/if}

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

{#snippet connectForm()}
	<form class="connect" onsubmit={submit}>
		<div class="grid">
			<label class="field">
				<span class="k-mono flabel">OWNER</span>
				<input class="k-mono" bind:value={owner} aria-label="repository owner" placeholder="acme" />
			</label>
			<label class="field">
				<span class="k-mono flabel">REPOSITORY</span>
				<input class="k-mono" bind:value={name} aria-label="repository name" placeholder="widgets" />
			</label>
			<label class="field wide">
				<span class="k-mono flabel">PR PIPELINE</span>
				<input
					class="k-mono"
					bind:value={prPipeline}
					aria-label="pull request pipeline"
					placeholder="/etc/kontinuance/pr.yaml"
				/>
			</label>
			<label class="field wide">
				<span class="k-mono flabel">PUSH PIPELINE <span class="opt">optional</span></span>
				<input
					class="k-mono"
					bind:value={pushPipeline}
					aria-label="push pipeline"
					placeholder="/etc/kontinuance/deliver.yaml"
				/>
			</label>
			<label class="field">
				<span class="k-mono flabel">TRACKED BRANCH</span>
				<input class="k-mono" bind:value={trackedBranch} aria-label="tracked branch" />
			</label>
			<label class="field">
				<span class="k-mono flabel">POLL INTERVAL (S)</span>
				<input
					class="k-mono"
					type="number"
					min="10"
					bind:value={pollIntervalSeconds}
					aria-label="poll interval seconds"
				/>
			</label>
			<label class="field wide">
				<span class="k-mono flabel">
					ACCESS TOKEN
					<span class="opt">{source?.hasToken ? 'a token is already stored — leave blank to keep it' : 'required'}</span>
				</span>
				<input
					class="k-mono"
					type="password"
					autocomplete="off"
					bind:value={token}
					aria-label="github access token"
					placeholder="ghp_…"
				/>
			</label>
		</div>

		<div class="fnote k-mono">
			The token needs <code>repo:status</code> to report checks, and read access to the repository. It is
			stored on the server, readable only by the server's user, and never returned by the API.
		</div>

		{#if connectError}
			<div class="k-mono cerr">{connectError}</div>
		{/if}

		<button class="k-mono submit" type="submit" disabled={!canSubmit}>
			{busy ? 'CONNECTING…' : 'CONNECT'}
		</button>
	</form>
{/snippet}

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
		align-items: center;
		gap: 14px;
		padding: 40px 16px;
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
		text-align: center;
	}

	/* connect form */
	.connect {
		width: 100%;
		max-width: 720px;
		display: flex;
		flex-direction: column;
		gap: 16px;
		border: 1px solid var(--k-border);
		background: var(--k-surface-2);
		border-radius: 6px;
		padding: 20px 22px;
		text-align: left;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 14px;
	}
	.field {
		display: flex;
		flex-direction: column;
		gap: 6px;
		min-width: 0;
	}
	.field.wide {
		grid-column: 1 / -1;
	}
	.flabel {
		font-size: 9px;
		letter-spacing: 2px;
		color: var(--k-faint);
	}
	.opt {
		letter-spacing: 0.5px;
		text-transform: none;
		color: var(--k-muted-4);
	}
	.connect input {
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 5px;
		padding: 9px 11px;
		font-size: 11.5px;
		color: var(--k-text);
	}
	.connect input:focus {
		outline: none;
		border-color: rgba(94, 234, 212, 0.55);
	}
	.fnote {
		font-size: 10px;
		line-height: 1.7;
		color: var(--k-muted-4);
	}
	.cerr {
		font-size: 10.5px;
		line-height: 1.6;
		color: var(--k-fail);
	}
	.submit {
		align-self: flex-start;
		padding: 9px 24px;
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-teal);
		border: 1px solid rgba(94, 234, 212, 0.45);
		background: none;
		border-radius: 5px;
		cursor: pointer;
	}
	.submit:disabled {
		color: var(--k-muted-4);
		border-color: var(--k-border);
		cursor: not-allowed;
	}

	/* connected-state controls */
	.actions {
		display: inline-flex;
		gap: 8px;
		margin-left: auto;
	}
	.act {
		padding: 6px 14px;
		font-size: 9px;
		letter-spacing: 1.5px;
		color: var(--k-muted-3);
		border: 1px solid var(--k-border);
		background: none;
		border-radius: 5px;
		cursor: pointer;
	}
	.act.go {
		color: var(--k-teal);
		border-color: rgba(94, 234, 212, 0.45);
	}
	.act.danger:hover:not(:disabled) {
		color: var(--k-fail);
	}
	.act:disabled {
		cursor: not-allowed;
		opacity: 0.5;
	}
	.warn {
		font-size: 10.5px;
		line-height: 1.7;
		color: var(--k-muted-3);
		border: 1px solid var(--k-border);
		border-left: 2px solid var(--k-fail);
		border-radius: 5px;
		padding: 10px 14px;
		margin-bottom: 22px;
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
