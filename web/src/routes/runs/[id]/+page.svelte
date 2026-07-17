<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, ApiError } from '$lib/api/client';
	import type { Coverage, RunRecord } from '$lib/api/types';
	import RunDetail from '$lib/screens/RunDetail.svelte';

	let run = $state<RunRecord | null>(null);
	let coverage = $state<Coverage | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let notFound = $state(false);

	async function load() {
		loading = true;
		error = null;
		notFound = false;
		try {
			run = await api.getRun(page.params.id ?? '');
			// coverage is a best-effort sidebar; a failure there must not fail the run view
			coverage = await api.getCoverage().catch(() => null);
		} catch (e) {
			if (e instanceof ApiError && e.status === 404) notFound = true;
			else error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			loading = false;
		}
	}

	$effect(() => {
		load();
	});
</script>

<RunDetail
	{run}
	{coverage}
	{loading}
	{error}
	{notFound}
	onback={() => goto('/')}
	onretry={load}
/>
