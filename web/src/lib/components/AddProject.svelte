<script lang="ts">
	// The add-project panel (032/041): name + descriptor + optional source repo/branch. Extracted
	// unchanged from Login.svelte's project picker — behavior is identical to before the move.
	import { api, ApiError } from '$lib/api/client';

	let { onadded, onclose }: { onadded?: (name: string) => void; onclose?: () => void } = $props();

	let newName = $state('');
	let newText = $state('');
	let newRepo = $state('');
	let newBranch = $state('');
	let adding = $state(false);
	let addError = $state<string | null>(null);

	async function addProject() {
		if (adding || !newName.trim() || !newText.trim()) return;
		adding = true;
		addError = null;
		try {
			await api.addProject(newName.trim(), newText, newRepo.trim(), newBranch.trim());
			const name = newName.trim();
			newName = '';
			newText = '';
			newRepo = '';
			newBranch = '';
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
</style>
