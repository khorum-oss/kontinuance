<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { Coverage } from '$lib/api/types';
	import CoverageScreen from '$lib/screens/Coverage.svelte';

	let coverage = $state<Coverage | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);

	async function load() {
		loading = true;
		error = null;
		try {
			coverage = await api.getCoverage();
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

<CoverageScreen {coverage} {loading} {error} onretry={load} />
