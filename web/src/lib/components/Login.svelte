<script lang="ts">
	// Entry shell: sign-in step → repo-setup selection → enter. The sign-in step calls the real server
	// (016): SIGN IN posts the credentials; a wrong pair shows an error. When the server is open (or a
	// session already exists) the layout passes [requireSignIn]=false and this starts on the repo step.
	import { untrack } from 'svelte';
	import { api, ApiError } from '$lib/api/client';

	export interface RepoSetup {
		name: string;
		desc: string;
	}

	const defaultRepos: RepoSetup[] = [
		{ name: 'kontinuance-service', desc: 'gradle · kontinuance.yml · deploys to stage' },
		{ name: 'legacy-adapter', desc: 'maven · pom bridge · jar publish only' },
		{ name: 'infra-charts', desc: 'helm · argo app-of-apps · no build phase' }
	];

	let {
		repos = defaultRepos,
		requireSignIn = false,
		operator = '',
		onauthenticated,
		oncomplete,
		onsignout
	}: {
		repos?: RepoSetup[];
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
	let picked = $state('');
	let error = $state<string | null>(null);
	let signingIn = $state(false);
	// True when a real session backs this view (just signed in, or a returning session) — drives the
	// "signed in as" identity row and the sign-out control, both hidden in open mode.
	let authed = $state(untrack(() => !requireSignIn && operator !== ''));

	$effect(() => {
		if (picked === '') picked = repos[0]?.name ?? '';
	});

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
</script>

<div class="overlay">
	<div class="panel-wrap">
		<div class="mark"><div class="core"></div></div>
		<div class="brand">KONTINUANCE</div>
		<div class="tag k-mono">MISSION CONTROL ACCESS</div>

		{#if step === 'auth'}
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
		{:else}
			<div class="card">
				{#if authed}
					<div class="signed k-mono">
						<span class="ok-dot"></span> signed in as
						<span class="who">{user || operator || 'operator'}</span>
						<button class="switch" onclick={() => onsignout?.()}>SIGN OUT</button>
					</div>
				{/if}
				<div class="k-mono label">SELECT REPO SETUP</div>
				{#each repos as r (r.name)}
					<button
						class="repo"
						class:sel={picked === r.name}
						onclick={() => (picked = r.name)}
					>
						<span class="diamond"></span>
						<span class="rcol">
							<span class="k-mono rname">{r.name}</span>
							<span class="k-mono rdesc">{r.desc}</span>
						</span>
					</button>
				{/each}
				<button class="k-mono enter" onclick={() => oncomplete?.(picked)}>ENTER MISSION CONTROL</button>
			</div>
		{/if}
		<div class="foot k-mono">CI // ORBITAL v0.4 · AUTH SCOPED PER REPO SETUP</div>
	</div>
</div>

<style>
	.overlay {
		position: fixed;
		inset: 0;
		z-index: 50;
		display: flex;
		align-items: center;
		justify-content: center;
		background:
			radial-gradient(900px 600px at 50% 30%, rgba(94, 234, 212, 0.06), transparent 65%),
			var(--k-bg);
	}
	.panel-wrap {
		width: 420px;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.mark {
		width: 44px;
		height: 44px;
		border: 2px solid var(--k-teal);
		transform: rotate(45deg);
		display: flex;
		align-items: center;
		justify-content: center;
		margin-bottom: 26px;
	}
	.core {
		width: 14px;
		height: 14px;
		background: var(--k-teal);
		animation: kpulsesoft 3.6s ease-in-out infinite;
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
		background: rgba(13, 20, 28, 0.85);
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
		padding: 12px 16px;
		background: rgba(9, 14, 19, 0.8);
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
	.signed {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 10px;
		color: var(--k-muted-3);
	}
	.ok-dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--k-ok);
	}
	.who {
		color: var(--k-text);
	}
	.switch {
		margin-left: auto;
		font-size: 9px;
		letter-spacing: 1px;
		color: var(--k-faint);
		background: none;
		border: none;
		cursor: pointer;
	}
	.switch:hover {
		color: var(--k-teal);
	}
	.repo {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 16px;
		border: 1px solid var(--k-border);
		background: none;
		border-radius: 5px;
		cursor: pointer;
		text-align: left;
	}
	.repo:hover,
	.repo.sel {
		border-color: rgba(94, 234, 212, 0.5);
	}
	.diamond {
		width: 7px;
		height: 7px;
		flex: none;
		transform: rotate(45deg);
		border: 1px solid var(--k-teal);
	}
	.repo.sel .diamond {
		background: var(--k-teal);
	}
	.rcol {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.rname {
		font-size: 11.5px;
		color: var(--k-text);
	}
	.rdesc {
		font-size: 9.5px;
		color: var(--k-muted-4);
	}
	.foot {
		font-size: 9px;
		letter-spacing: 1.5px;
		color: var(--k-faint-2);
		margin-top: 22px;
	}
</style>
