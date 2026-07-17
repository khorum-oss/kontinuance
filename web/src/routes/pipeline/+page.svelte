<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { Pipeline } from '$lib/api/types';
	import PipelineScreen from '$lib/screens/Pipeline.svelte';

	let pipeline = $state<Pipeline | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);

	async function load() {
		loading = true;
		error = null;
		try {
			// Show the newest run's pipeline; fall back to a generic handle if the list is unavailable.
			let id = 'latest';
			try {
				const runs = await api.listRuns(1);
				if (runs[0]) id = runs[0].id;
			} catch {
				/* keep the fallback id */
			}
			pipeline = await api.getPipeline(id);
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

<PipelineScreen {pipeline} {loading} {error} onretry={load} />
