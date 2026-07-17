<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { Config } from '$lib/api/types';
	import ConfigScreen from '$lib/screens/Config.svelte';

	let config = $state<Config | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);

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

	$effect(() => {
		load();
	});
</script>

<ConfigScreen {config} {loading} {error} onretry={load} />
