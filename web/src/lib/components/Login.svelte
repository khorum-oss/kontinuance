<script lang="ts">
	// Entry shell: sign-in step → project picker → enter. The sign-in step calls the real server (016):
	// SIGN IN posts the credentials; a wrong pair shows an error. When the server is open (or a session
	// already exists) the layout passes [requireSignIn]=false and this starts on the project picker.
	//
	// The project picker is the first-run setup (032): the named pipeline descriptors ("projects") the
	// server stores are listed from `/api/projects`; you add one (name + descriptor, validated server-side)
	// and selecting a project **activates** it — the server points its live descriptor at it, so the trigger
	// and Config screen use it — then enters mission control.
	import { onMount, untrack } from 'svelte';
	import { api, ApiError } from '$lib/api/client';
	import type { Project } from '$lib/api/types';

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
		oncomplete?: (project: string) => void;
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

	// project picker state
	let projects = $state<Project[]>([]);
	let loadingProjects = $state(true);
	let projectsError = $state<string | null>(null);
	let selecting = $state<string | null>(null);

	// add-project panel state
	let addOpen = $state(false);
	let newName = $state('');
	let newText = $state('');
	let newRepo = $state('');
	let newBranch = $state('');
	let adding = $state(false);
	let addError = $state<string | null>(null);

	// per-card source editor state (033) — one project open at a time
	let sourceEditing = $state<string | null>(null);
	let srcRepo = $state('');
	let srcBranch = $state('');
	let savingSource = $state(false);
	let sourceError = $state<string | null>(null);

	const who = $derived(user || operator || 'operator');

	async function loadProjects() {
		loadingProjects = true;
		projectsError = null;
		try {
			projects = (await api.getProjects()).projects;
		} catch (e) {
			projectsError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			loadingProjects = false;
		}
	}

	onMount(() => {
		if (step === 'repo') loadProjects();
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
			loadProjects();
		} catch (e) {
			error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			signingIn = false;
		}
	}

	async function selectProject(name: string) {
		if (selecting) return;
		selecting = name;
		projectsError = null;
		try {
			await api.activateProject(name);
			oncomplete?.(name);
		} catch (e) {
			projectsError = e instanceof ApiError ? e.message : (e as Error).message;
			selecting = null;
		}
	}

	async function addProject() {
		if (adding || !newName.trim() || !newText.trim()) return;
		adding = true;
		addError = null;
		try {
			await api.addProject(newName.trim(), newText, newRepo.trim(), newBranch.trim());
			addOpen = false;
			newName = '';
			newText = '';
			newRepo = '';
			newBranch = '';
			await loadProjects();
		} catch (e) {
			addError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			adding = false;
		}
	}

	function toggleSource(p: Project) {
		sourceError = null;
		if (sourceEditing === p.name) {
			sourceEditing = null;
			return;
		}
		sourceEditing = p.name;
		srcRepo = p.repo ?? '';
		srcBranch = p.branch ?? '';
	}

	async function saveSource(name: string) {
		if (savingSource) return;
		savingSource = true;
		sourceError = null;
		try {
			await api.setProjectSource(name, srcRepo.trim(), srcBranch.trim());
			sourceEditing = null;
			await loadProjects();
		} catch (e) {
			sourceError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			savingSource = false;
		}
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
			<div class="foot k-mono">CI // ORBITAL v0.4 · AUTH SCOPED PER PROJECT</div>
		</div>
	</div>
{:else}
	<!-- ===== step 2: project picker (full screen) ===== -->
	<div class="overlay ws">
		<div class="ws-inner">
			<!-- header -->
			<div class="ws-head">
				<div class="mark sm"><div class="core sm"></div></div>
				<span class="ws-brand">KONTINUANCE</span>
				<span class="k-mono ws-sub">// SELECT PROJECT</span>
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
				<button class="k-mono add" onclick={() => (addOpen = !addOpen)}>+ ADD PROJECT</button>
				<span class="k-mono tool-hint">a project is a named <code>kontinuance.yml</code> the server runs</span>
			</div>

			<!-- add project panel -->
			{#if addOpen}
				<div class="add-panel">
					<div class="add-top">
						<span class="k-mono add-title">ADD PROJECT</span>
						<button class="k-mono link close" onclick={() => (addOpen = false)}>✕ CLOSE</button>
					</div>
					<input
						class="k-mono field"
						placeholder="project name — e.g. my-service"
						spellcheck="false"
						bind:value={newName}
					/>
					<div class="src-row">
						<input
							class="k-mono field"
							aria-label="new project repo"
							placeholder="repo URL (optional) — https://…"
							spellcheck="false"
							bind:value={newRepo}
						/>
						<input
							class="k-mono field"
							aria-label="new project branch"
							placeholder="branch, tag, or commit SHA (optional)"
							spellcheck="false"
							bind:value={newBranch}
						/>
					</div>
					<textarea
						class="k-mono editor"
						aria-label="descriptor source"
						placeholder="pipeline:&#10;  name: &quot;my-service&quot;&#10;  stages: …"
						spellcheck="false"
						bind:value={newText}
					></textarea>
					{#if addError}
						<div class="k-mono add-err" role="alert">{addError}</div>
					{/if}
					<div class="add-row">
						<button
							class="k-mono add-btn"
							disabled={adding || !newName.trim() || !newText.trim()}
							onclick={addProject}
						>
							{adding ? 'SAVING…' : 'SAVE PROJECT'}
						</button>
					</div>
					<div class="k-mono add-help">
						the descriptor is validated by the engine parser — an invalid one is rejected, not stored
					</div>
				</div>
			{/if}

			<!-- project grid -->
			<div class="ws-body">
				{#if loadingProjects}
					<div class="note k-mono">loading projects…</div>
				{:else if projectsError}
					<div class="note k-mono err-note">{projectsError}</div>
				{:else if projects.length === 0}
					<div class="note k-mono">no projects yet — add one to get started</div>
				{:else}
					<div class="repos">
						{#each projects as p (p.name)}
							<div class="repo" class:activ={p.active}>
								<button
									class="repo-activate"
									disabled={selecting !== null}
									onclick={() => selectProject(p.name)}
								>
									<span class="repo-main">
										<span class="rmark" style:background={p.active ? 'var(--k-teal)' : 'transparent'}></span>
										<span class="rcol">
											<span class="k-mono rname">{p.name}</span>
											<span class="k-mono rdesc">
												{#if selecting === p.name}activating…
												{:else if p.repo}{p.repo}{p.branch ? ` · ${p.branch}` : ''}
												{:else}no source · runs the descriptor as-is{/if}
											</span>
										</span>
									</span>
									<span class="badges">
										<span class="k-mono badge" class:cfg={p.active}>{p.active ? 'ACTIVE' : 'AVAILABLE'}</span>
									</span>
								</button>
								<div class="src-foot">
									<button class="k-mono link src-toggle" onclick={() => toggleSource(p)}>
										{sourceEditing === p.name ? '✕ CLOSE' : p.repo ? 'EDIT SOURCE' : 'SET SOURCE'}
									</button>
								</div>
								{#if sourceEditing === p.name}
									<div class="src-edit">
										<input
											class="k-mono field"
											aria-label="source repo"
											placeholder="repo URL — https://…"
											spellcheck="false"
											bind:value={srcRepo}
										/>
										<input
											class="k-mono field"
											aria-label="source branch"
											placeholder="branch, tag, or commit SHA (optional)"
											spellcheck="false"
											bind:value={srcBranch}
										/>
										{#if sourceError}
											<div class="k-mono add-err" role="alert">{sourceError}</div>
										{/if}
										<button
											class="k-mono add-btn"
											disabled={savingSource}
											onclick={() => saveSource(p.name)}
										>
											{savingSource ? 'SAVING…' : 'SAVE SOURCE'}
										</button>
									</div>
								{/if}
							</div>
						{/each}
					</div>
				{/if}
			</div>

			<!-- footer -->
			<div class="ws-foot">
				<span class="k-mono">{projects.length} project{projects.length === 1 ? '' : 's'}</span>
				<span class="k-mono hint">CLICK A PROJECT TO ACTIVATE IT AND ENTER MISSION CONTROL</span>
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
		gap: 16px;
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
	.tool-hint {
		font-size: 9.5px;
		color: var(--k-faint);
	}
	.tool-hint code {
		color: var(--k-muted-3);
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
	.editor {
		width: 100%;
		box-sizing: border-box;
		min-height: 160px;
		resize: vertical;
		padding: 12px 16px;
		background: var(--k-surface-2);
		border: 1px solid var(--k-border);
		border-radius: 5px;
		color: var(--k-text);
		font-size: 11.5px;
		line-height: 1.7;
		outline: none;
	}
	.editor:focus {
		border-color: rgba(94, 234, 212, 0.55);
	}
	.add-err {
		font-size: 10px;
		color: var(--k-fail);
		white-space: pre-wrap;
	}
	.add-row {
		display: flex;
		gap: 10px;
	}
	.add-btn {
		flex: none;
		padding: 10px 24px;
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
	.note {
		padding: 40px 16px;
		text-align: center;
		font-size: 11px;
		color: var(--k-muted-4);
	}
	.err-note {
		color: var(--k-fail);
	}
	.repos {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: 12px;
	}
	.repo {
		display: flex;
		flex-direction: column;
		border: 1px solid var(--k-border);
		background: var(--k-surface-2);
		border-radius: 6px;
	}
	.repo:hover {
		border-color: rgba(94, 234, 212, 0.5);
	}
	.repo.activ {
		border-color: rgba(52, 211, 153, 0.35);
	}
	.repo-activate {
		display: flex;
		align-items: center;
		gap: 14px;
		width: 100%;
		padding: 16px 18px;
		background: none;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		text-align: left;
	}
	.repo-activate:disabled {
		opacity: 0.6;
		cursor: default;
	}
	.src-foot {
		display: flex;
		padding: 0 18px 12px;
	}
	.src-toggle {
		font-size: 8.5px;
		letter-spacing: 1px;
	}
	.src-toggle:hover {
		color: var(--k-teal);
	}
	.src-edit {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 0 18px 16px;
	}
	.src-row {
		display: flex;
		gap: 10px;
	}
	.src-row .field {
		flex: 1;
		min-width: 0;
	}
	.src-edit .add-btn {
		align-self: flex-start;
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
