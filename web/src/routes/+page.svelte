<script lang="ts">
	import { goto } from '$app/navigation';
	import { api, ApiError } from '$lib/api/client';
	import { toRunView, type RunView } from '$lib/api/present';
	import Runs from '$lib/screens/Runs.svelte';

	let runs = $state<RunView[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	async function load() {
		loading = true;
		error = null;
		try {
			const records = await api.listRuns(100);
			runs = records.map((r) => toRunView(r));
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

<Runs {runs} {loading} {error} onopen={(id) => goto(`/runs/${id}`)} onretry={load} />
