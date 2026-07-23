<script lang="ts">
	// Entry shell: sign-in step → repo workspace → enter. The sign-in step calls the real server (016):
	// SIGN IN posts the credentials; a wrong pair shows an error. When the server is open (or a session
	// already exists) the layout passes [requireSignIn]=false and this starts on the repo workspace.
	//
	// The repo workspace is the first-run repo experience: browse configured/available repo setups, filter
	// by provider, switch card/list layout, and add a repo (GitHub / GitLab / a git URL). Added repos are
	// remembered per browser (localStorage) — a real repo-config backend is a later feature; clicking a repo
	// enters mission control.
	import { onMount, untrack } from 'svelte';
	import { api, ApiError } from '$lib/api/client';

	type Provider = 'github' | 'gitlab' | 'manual';
	export interface RepoSetup {
		name: string;
		desc: string;
		prov: Provider;
		cfg: boolean;
	}

	const REPOS_KEY = 'knt-repos';
	const seedRepos: RepoSetup[] = [
		{ name: 'kontinuance-service', desc: 'gradle · deploys to stage', prov: 'github', cfg: true },
		{ name: 'legacy-adapter', desc: 'maven · pom bridge · jar publish only', prov: 'github', cfg: true },
		{ name: 'infra-charts', desc: 'helm · argo app-of-apps · no build phase', prov: 'gitlab', cfg: true },
		{ name: 'kontinuance-dsl', desc: 'gradle · parser + tokenizer', prov: 'github', cfg: false },
		{ name: 'registry-proxy', desc: 'gradle · artifact cache layer', prov: 'gitlab', cfg: false },
		{ name: 'kntc-web', desc: 'sveltekit · phase 2 npm/bun lane', prov: 'github', cfg: false }
	];
	const provColor: Record<Provider, string> = {
		github: '#c4b5fd',
		gitlab: '#fb923c',
		manual: '#7dd3fc'
	};

	let {
		requireSignIn = false,
		operator = '',
		onauthenticated,
		oncomplete,
		onsignout
	}: {
		requireSignIn?: boolean;
		operator?: string;
		onauthenticated?: (username: string) => void;
		oncomplete?: (repo: string) => void;
		onsignout?: () => void;
	} = $props();

	// Seeded once from the props at mount (the layout re-creates this component per view, so the initial
	// capture is intentional — untrack silences the "referenced locally" hint).
	let step = $state<'auth' | 'repo'>(untrack(() => (requireSignIn ? 'auth' : 'repo')));
	let user = $state(untrack(() => operator));
	let password = $state('');
	let error = $state<string | null>(null);
	let signingIn = $state(false);
	let authed = $state(untrack(() => !requireSignIn && operator !== ''));

	// repo workspace state
	let repos = $state<RepoSetup[]>([...seedRepos]);
	let repoSrc = $state<'all' | Provider>('all');
	let layout = $state<'cards' | 'list'>('cards');
	let addOpen = $state(false);
	let addProv = $state<Provider>('github');
	let manualUrl = $state('');
	let manualBranch = $state('');

	const who = $derived(user || operator || 'operator');
	const shown = $derived(repos.filter((r) => repoSrc === 'all' || r.prov === repoSrc));
	const configured = $derived(repos.filter((r) => r.cfg).length);
	const filters: Array<'all' | Provider> = ['all', 'github', 'gitlab', 'manual'];
	const addProviders: Provider[] = ['github', 'gitlab', 'manual'];
	function count(src: 'all' | Provider): number {
		return src === 'all' ? repos.length : repos.filter((r) => r.prov === src).length;
	}
	const addPlaceholder = $derived(
		addProv === 'github'
			? 'org/repo — e.g. you/your-app'
			: addProv === 'gitlab'
				? 'group/project — e.g. team/service'
				: 'git url — https://… or git@…'
	);

	onMount(() => {
		const saved = localStorage.getItem(REPOS_KEY);
		if (saved) {
			try {
				const parsed = JSON.parse(saved) as RepoSetup[];
				if (Array.isArray(parsed) && parsed.length) repos = parsed;
			} catch {
				// ignore a corrupt cache; fall back to the seeds
			}
		}
	});

	function persist() {
		localStorage.setItem(REPOS_KEY, JSON.stringify(repos));
	}

	async function signIn() {
		if (signingIn) return;
		error = null;
		signingIn = true;
		try {
			const session = await api.login(user, password);
			authed = true;
			onauthenticated?.(session.username ?? user);
			password = '';
			step = 'repo';
		} catch (e) {
			error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			signingIn = false;
		}
	}

	function addRepo() {
		if (!manualUrl.trim()) return;
		const name = (manualUrl.split('/').pop() || 'repo').replace(/\.git$/, '');
		repos = [{ name, desc: `${manualBranch || 'main'} · awaiting first scan`, prov: addProv, cfg: false }, ...repos];
		persist();
		manualUrl = '';
		manualBranch = '';
		addOpen = false;
		repoSrc = 'all';
	}
</script>

{#if step === 'auth'}
	<!-- ===== step 1: sign in (centered card) ===== -->
	<div class="overlay">
		<div class="panel-wrap">
			<div class="mark"><div class="core"></div></div>
			<div class="brand">KONTINUANCE</div>
			<div class="tag k-mono">MISSION CONTROL ACCESS</div>
			<div class="card">
				<div class="k-mono label">OPERATOR CREDENTIALS</div>
				<input
					class="k-mono field"
					placeholder="username"
					spellcheck="false"
					bind:value={user}
					onkeydown={(e) => e.key === 'Enter' && signIn()}
				/>
				<input
					class="k-mono field"
					placeholder="password"
					type="password"
					bind:value={password}
					onkeydown={(e) => e.key === 'Enter' && signIn()}
				/>
				{#if error}
					<div class="k-mono err" role="alert">{error}</div>
				{/if}
				<button class="k-mono enter" onclick={signIn} disabled={signingIn}>
					{signingIn ? 'SIGNING IN…' : 'SIGN IN'}
				</button>
			</div>
			<div class="foot k-mono">CI // ORBITAL v0.4 · AUTH SCOPED PER REPO SETUP</div>
		</div>
	</div>
{:else}
	<!-- ===== step 2: repo workspace (full screen) ===== -->
	<div class="overlay ws">
		<div class="ws-inner">
			<!-- header -->
			<div class="ws-head">
				<div class="mark sm"><div class="core sm"></div></div>
				<span class="ws-brand">KONTINUANCE</span>
				<span class="k-mono ws-sub">// SELECT REPO SETUP</span>
				<div class="ws-who">
					<span class="dot ok"></span>
					<span class="k-mono">{who}</span>
					{#if authed}
						<button class="k-mono link danger" onclick={() => onsignout?.()}>SIGN OUT</button>
					{/if}
				</div>
			</div>

			<!-- toolbar -->
			<div class="ws-tools">
				<button class="k-mono add" onclick={() => (addOpen = !addOpen)}>+ ADD REPO</button>
				<div class="chips">
					{#each filters as f (f)}
						<button
							class="chip"
							class:sel={repoSrc === f}
							aria-label="filter {f}"
							onclick={() => (repoSrc = f)}
						>
							<span class="cdot" style:background={f === 'all' ? 'var(--k-teal)' : provColor[f]}></span>
							<span class="k-mono clabel">{f.toUpperCase()}</span>
							<span class="k-mono ccount">{count(f)}</span>
						</button>
					{/each}
				</div>
				<div class="lyt">
					<button class="ly" class:on={layout === 'cards'} aria-label="card layout" onclick={() => (layout = 'cards')}>
						<span class="lc"></span><span class="lc"></span>
					</button>
					<button class="ly" class:on={layout === 'list'} aria-label="list layout" onclick={() => (layout = 'list')}>
						<span class="ll"></span><span class="ll"></span><span class="ll"></span>
					</button>
				</div>
			</div>

			<!-- add repo panel -->
			{#if addOpen}
				<div class="add-panel">
					<div class="add-top">
						<span class="k-mono add-title">ADD REPO</span>
						<button class="k-mono link close" onclick={() => (addOpen = false)}>✕ CLOSE</button>
					</div>
					<div class="add-provs">
						{#each addProviders as p (p)}
							<button class="add-prov" class:sel={addProv === p} onclick={() => (addProv = p)}>
								<span class="cdot" style:background={provColor[p]}></span>
								<span class="k-mono">{p === 'manual' ? 'GIT URL' : `FROM ${p.toUpperCase()}`}</span>
							</button>
						{/each}
					</div>
					<div class="add-row">
						<input class="k-mono field grow" placeholder={addPlaceholder} spellcheck="false" bind:value={manualUrl} />
						<input class="k-mono field" placeholder="branch (main)" spellcheck="false" bind:value={manualBranch} />
						<button class="k-mono add-btn" disabled={!manualUrl.trim()} onclick={addRepo}>ADD REPO</button>
					</div>
					<div class="k-mono add-help">
						kontinuance.yml is read from the repo root on first scan · each repo carries its own run config
					</div>
				</div>
			{/if}

			<!-- repo grid / list -->
			<div class="ws-body">
				<div class="repos" class:list={layout === 'list'}>
					{#each shown as r (r.name)}
						<button class="repo" class:list={layout === 'list'} onclick={() => oncomplete?.(r.name)}>
							<span class="repo-main">
								<span class="rmark" style:background={r.cfg ? 'var(--k-teal)' : 'transparent'}></span>
								<span class="rcol">
									<span class="k-mono rname">{r.name}</span>
									<span class="k-mono rdesc">{r.desc}</span>
								</span>
							</span>
							<span class="badges">
								<span
									class="k-mono badge"
									style="color:{provColor[r.prov]}; border-color:{provColor[r.prov]}55;"
								>
									{r.prov.toUpperCase()}
								</span>
								<span class="k-mono badge" class:cfg={r.cfg}>{r.cfg ? 'CONFIGURED' : 'AVAILABLE'}</span>
							</span>
						</button>
					{/each}
				</div>
			</div>

			<!-- footer -->
			<div class="ws-foot">
				<span class="k-mono">{count(repoSrc)} repo setups · {configured} configured</span>
				<span class="k-mono hint">CLICK A REPO TO ENTER MISSION CONTROL</span>
			</div>
		</div>
	</div>
{/if}

<style>
	.overlay {
		position: fixed;
		inset: 0;
		z-index: 50;
		display: flex;
		align-items: center;
		justify-content: center;
		background:
			radial-gradient(900px 600px at 50% 30%, var(--k-glow), transparent 65%),
			var(--k-bg);
	}
	.overlay.ws {
		align-items: stretch;
		justify-content: stretch;
	}
	/* ----- shared mark ----- */
	.mark {
		width: 44px;
		height: 44px;
		flex: none;
		border: 2px solid var(--k-teal);
		transform: rotate(45deg);
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 26px;
	}
	.mark.sm {
		width: 22px;
		height: 22px;
		border-width: 1.5px;
		margin-bottom: 0;
	}
	.core {
		width: 14px;
		height: 14px;
		background: var(--k-teal);
		animation: kpulsesoft 3.6s ease-in-out infinite;
	}
	.core.sm {
		width: 7px;
		height: 7px;
		animation: none;
	}
	/* ----- step 1 card ----- */
	.panel-wrap {
		width: 420px;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.brand {
		font-weight: 700;
		font-size: 22px;
		letter-spacing: 5px;
		color: var(--k-heading);
	}
	.tag {
		font-size: 10px;
		letter-spacing: 2.5px;
		color: var(--k-muted-4);
		margin: 8px 0 34px;
	}
	.card {
		width: 100%;
		background: var(--k-surface);
		border: 1px solid var(--k-border);
		border-radius: 8px;
		padding: 24px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.label {
		font-size: 9.5px;
		letter-spacing: 2px;
		color: var(--k-faint);
	}
	.field {
		width: 100%;
		box-sizing: border-box;
		padding: 12px 16px;
		background: var(--k-surface-2);
		border: 1px solid var(--k-border);
		border-radius: 5px;
		color: var(--k-text);
		font-size: 11.5px;
		letter-spacing: 0.5px;
		outline: none;
	}
	.field:focus {
		border-color: rgba(94, 234, 212, 0.55);
	}
	.enter {
		text-align: center;
		font-size: 11px;
		letter-spacing: 2.5px;
		padding: 13px;
		border-radius: 5px;
		color: var(--k-teal);
		border: 1px solid rgba(94, 234, 212, 0.45);
		background: rgba(94, 234, 212, 0.07);
		cursor: pointer;
		transition: background 0.25s;
	}
	.enter:hover {
		background: rgba(94, 234, 212, 0.14);
	}
	.enter:disabled {
		opacity: 0.6;
		cursor: default;
	}
	.err {
		font-size: 10px;
		letter-spacing: 0.5px;
		color: var(--k-fail);
		background: rgba(248, 113, 113, 0.08);
		border: 1px solid rgba(248, 113, 113, 0.3);
		border-radius: 5px;
		padding: 9px 12px;
	}
	.foot {
		font-size: 9px;
		letter-spacing: 1.5px;
		color: var(--k-faint-2);
		margin-top: 22px;
	}
	/* ----- step 2 workspace ----- */
	.ws-inner {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.ws-head {
		flex: none;
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 18px 32px;
		border-bottom: 1px solid var(--k-border-soft);
	}
	.ws-brand {
		font-weight: 700;
		font-size: 13px;
		letter-spacing: 2.5px;
		color: var(--k-heading);
	}
	.ws-sub {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-faint);
	}
	.ws-who {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: 12px;
		font-size: 10px;
		color: var(--k-muted-3);
	}
	.dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
	}
	.dot.ok {
		background: var(--k-ok);
	}
	.link {
		background: none;
		border: none;
		cursor: pointer;
		font-size: 9px;
		letter-spacing: 1px;
		color: var(--k-faint);
	}
	.link.danger:hover {
		color: var(--k-fail);
	}
	.ws-tools {
		flex: none;
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 16px 32px;
	}
	.add {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 11px 20px;
		border: none;
		border-radius: 5px;
		font-size: 11px;
		letter-spacing: 2px;
		color: #07110e;
		background: var(--k-teal);
		cursor: pointer;
		font-weight: 600;
	}
	.add:hover {
		background: var(--k-teal-bright);
	}
	.chips {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.chip {
		display: flex;
		align-items: center;
		gap: 7px;
		padding: 9px 14px;
		border: 1px solid var(--k-border);
		background: none;
		border-radius: 4px;
		cursor: pointer;
	}
	.chip:hover,
	.chip.sel {
		border-color: rgba(94, 234, 212, 0.5);
	}
	.chip.sel {
		background: rgba(94, 234, 212, 0.06);
	}
	.cdot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
	}
	.clabel {
		font-size: 10px;
		letter-spacing: 1.5px;
		color: var(--k-muted-3);
	}
	.chip.sel .clabel {
		color: var(--k-heading);
	}
	.ccount {
		font-size: 9px;
		color: var(--k-faint);
	}
	.lyt {
		margin-left: auto;
		display: flex;
		border: 1px solid var(--k-border);
		border-radius: 4px;
		overflow: hidden;
	}
	.ly {
		display: flex;
		gap: 2px;
		align-items: center;
		justify-content: center;
		padding: 8px 12px;
		background: none;
		border: none;
		cursor: pointer;
	}
	.ly.on {
		background: rgba(94, 234, 212, 0.1);
	}
	.ly:hover {
		background: rgba(94, 234, 212, 0.1);
	}
	.lc {
		width: 5px;
		height: 10px;
		border: 1px solid var(--k-faint);
	}
	.ly.on .lc {
		border-color: var(--k-teal);
	}
	.ly:nth-child(2) {
		flex-direction: column;
		gap: 2px;
	}
	.ll {
		width: 13px;
		height: 2px;
		background: var(--k-faint);
	}
	.ly.on .ll {
		background: var(--k-teal);
	}
	.add-panel {
		flex: none;
		margin: 0 32px 16px;
		padding: 20px;
		border: 1px solid rgba(94, 234, 212, 0.3);
		background: rgba(94, 234, 212, 0.03);
		border-radius: 8px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.add-top {
		display: flex;
		align-items: center;
	}
	.add-title {
		font-size: 10px;
		letter-spacing: 2px;
		color: var(--k-teal);
	}
	.close {
		margin-left: auto;
		font-size: 10px;
	}
	.close:hover {
		color: var(--k-fail);
	}
	.add-provs {
		display: flex;
		gap: 10px;
	}
	.add-prov {
		flex: 1;
		display: flex;
		align-items: center;
		gap: 9px;
		padding: 12px 16px;
		border: 1px solid var(--k-border);
		background: none;
		border-radius: 5px;
		cursor: pointer;
		font-size: 10.5px;
		letter-spacing: 1.5px;
		color: var(--k-muted-3);
	}
	.add-prov:hover,
	.add-prov.sel {
		border-color: rgba(94, 234, 212, 0.5);
	}
	.add-prov.sel {
		background: rgba(94, 234, 212, 0.07);
		color: var(--k-heading);
	}
	.add-row {
		display: flex;
		gap: 10px;
	}
	.grow {
		flex: 2.2;
	}
	.add-row .field:nth-child(2) {
		flex: 1;
	}
	.add-btn {
		flex: none;
		padding: 0 24px;
		font-size: 10.5px;
		letter-spacing: 2px;
		color: var(--k-teal);
		border: 1px solid rgba(94, 234, 212, 0.45);
		background: none;
		border-radius: 5px;
		cursor: pointer;
	}
	.add-btn:hover:not(:disabled) {
		background: rgba(94, 234, 212, 0.1);
	}
	.add-btn:disabled {
		opacity: 0.5;
		cursor: default;
	}
	.add-help {
		font-size: 9.5px;
		color: var(--k-faint);
	}
	.ws-body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: 4px 32px 20px;
	}
	.repos {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: 12px;
	}
	.repos.list {
		display: flex;
		flex-direction: column;
	}
	.repo {
		display: flex;
		flex-direction: column;
		align-items: stretch;
		gap: 10px;
		padding: 16px 18px;
		border: 1px solid var(--k-border);
		background: var(--k-surface-2);
		border-radius: 6px;
		cursor: pointer;
		text-align: left;
	}
	.repo.list {
		flex-direction: row;
		align-items: center;
		gap: 14px;
	}
	.repo:hover {
		border-color: rgba(94, 234, 212, 0.5);
	}
	.repo-main {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
		flex: 1;
	}
	.rmark {
		width: 7px;
		height: 7px;
		flex: none;
		transform: rotate(45deg);
		border: 1px solid var(--k-teal);
	}
	.rcol {
		display: flex;
		flex-direction: column;
		gap: 3px;
		min-width: 0;
	}
	.rname {
		font-size: 12.5px;
		color: var(--k-text);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.rdesc {
		font-size: 10px;
		color: var(--k-muted-4);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.badges {
		display: flex;
		align-items: center;
		gap: 8px;
		flex: none;
	}
	.badge {
		font-size: 8.5px;
		letter-spacing: 1px;
		padding: 3px 7px;
		border-radius: 3px;
		color: var(--k-muted-2);
		border: 1px solid var(--k-border);
	}
	.badge.cfg {
		color: var(--k-ok);
		border-color: rgba(52, 211, 153, 0.35);
	}
	.ws-foot {
		flex: none;
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 14px 32px;
		border-top: 1px solid var(--k-border-soft);
		font-size: 10px;
		color: var(--k-muted-4);
	}
	.hint {
		margin-left: auto;
		font-size: 9.5px;
		letter-spacing: 1px;
		color: var(--k-faint);
	}
</style>
