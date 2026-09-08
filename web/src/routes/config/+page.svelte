<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { Config } from '$lib/api/types';
	import ConfigScreen from '$lib/screens/Config.svelte';

	let config = $state<Config | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let saveError = $state<string | null>(null);
	let revertError = $state<string | null>(null);

	async function load() {
		loading = true;
		error = null;
		try {
			config = await api.getConfig();
		} catch (e) {
			error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			loading = false;
		}
	}

	// Persist an edited descriptor; on success update the view with the refreshed config, on a rejected
	// edit surface the server's validation message inline. Returns success so the editor can close.
	async function save(text: string): Promise<boolean> {
		saveError = null;
		try {
			config = await api.saveConfig(text);
			return true;
		} catch (e) {
			saveError = e instanceof ApiError ? e.message : (e as Error).message;
			return false;
		}
	}

	// Discards a stored override, restoring the repository's descriptor (041). A 409 ("nothing to
	// revert") is surfaced inline rather than treated as a failed page load.
	async function revert(): Promise<void> {
		revertError = null;
		try {
			config = await api.revertConfigOverride();
		} catch (e) {
			revertError = e instanceof ApiError ? e.message : (e as Error).message;
		}
	}

	$effect(() => {
		load();
	});
</script>

<ConfigScreen
	{config}
	{loading}
	{error}
	{saveError}
	{revertError}
	onretry={load}
	onsave={save}
	onrevert={revert}
/>
