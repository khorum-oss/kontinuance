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
	let deciding = $state(false);
	let decideError = $state<string | null>(null);

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

	async function decide(action: 'approve' | 'reject') {
		deciding = true;
		decideError = null;
		try {
			if (action === 'approve') await api.approveRun(page.params.id ?? '');
			else await api.rejectRun(page.params.id ?? '');
			await load();
		} catch (e) {
			decideError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			deciding = false;
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
	{deciding}
	{decideError}
	onback={() => goto('/')}
	onretry={load}
	onapprove={() => decide('approve')}
	onreject={() => decide('reject')}
/>
