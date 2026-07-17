<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { Deploy } from '$lib/api/types';
	import DeployScreen from '$lib/screens/Deploy.svelte';

	let deploy = $state<Deploy | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);

	async function load() {
		loading = true;
		error = null;
		try {
			deploy = await api.getDeploy();
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

<DeployScreen {deploy} {loading} {error} onretry={load} />
