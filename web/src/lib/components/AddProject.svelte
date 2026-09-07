<script lang="ts">
	// The add-project panel (032/041): name + a source repo/branch, since Kontinuance can read
	// kontinuance.yml out of the repository at run time. Pasting a descriptor directly is the exception —
	// it lives behind a disclosure toggle — for a project with no repository, or one whose descriptor
	// isn't checked in yet.
	import { api, ApiError } from '$lib/api/client';
	import { descriptorCheckMessage } from '$lib/api/present';

	let { onadded, onclose }: { onadded?: (name: string) => void; onclose?: () => void } = $props();

	let newName = $state('');
	let newRepo = $state('');
	let newBranch = $state('main');
	let newText = $state('');
	let showDescriptor = $state(false);
	let adding = $state(false);
	let addError = $state<string | null>(null);
	let check = $state<ReturnType<typeof descriptorCheckMessage>>(null);

	async function addProject() {
		if (adding || !newName.trim()) return;
		adding = true;
		addError = null;
		check = null;
		try {
			const created = await api.addProject(
				newName.trim(),
				newText,
				newRepo.trim(),
				newBranch.trim()
			);
			check = descriptorCheckMessage(created.descriptor);
			const name = newName.trim();
			newName = '';
			newRepo = '';
			newBranch = 'main';
			newText = '';
			showDescriptor = false;
			onadded?.(name);
		} catch (e) {
			addError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			adding = false;
		}
	}
</script>

<div class="add-panel">
	<div class="add-top">
		<span class="k-mono add-title">ADD PROJECT</span>
		<button class="k-mono link close" onclick={() => onclose?.()}>✕ CLOSE</button>
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
	<button
		class="k-mono link disclosure"
		onclick={() => (showDescriptor = !showDescriptor)}
	>
		{showDescriptor ? '✕ don’t paste a descriptor' : 'paste a descriptor instead'}
	</button>
	{#if showDescriptor}
		<textarea
			class="k-mono editor"
			aria-label="descriptor source"
			placeholder="pipeline:&#10;  name: &quot;my-service&quot;&#10;  stages: …"
			spellcheck="false"
			bind:value={newText}
		></textarea>
	{/if}
	{#if addError}
		<div class="k-mono add-err" role="alert">{addError}</div>
	{/if}
	{#if check}
		<div class="k-mono add-check" class:warn={check.tone === 'warn'}>{check.text}</div>
	{/if}
	<div class="add-row">
		<button class="k-mono add-btn" disabled={adding || !newName.trim()} onclick={addProject}>
			{adding ? 'SAVING…' : 'SAVE PROJECT'}
		</button>
	</div>
	<div class="k-mono add-help">
		a pasted descriptor is validated by the engine parser — an invalid one is rejected, not stored;
		otherwise the repository's <code>kontinuance.yml</code> is read when the project runs
	</div>
</div>

<style>
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
	.link {
		background: none;
		border: none;
		cursor: pointer;
		font-size: 9px;
		letter-spacing: 1px;
		color: var(--k-faint);
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
	.src-row {
		display: flex;
		gap: 10px;
	}
	.src-row .field {
		flex: 1;
		min-width: 0;
	}
	.disclosure {
		align-self: flex-start;
	}
	.disclosure:hover {
		color: var(--k-teal);
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
	.add-check {
		font-size: 10px;
		color: var(--k-ok);
		white-space: pre-wrap;
	}
	.add-check.warn {
		color: var(--k-warn);
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
</style>
