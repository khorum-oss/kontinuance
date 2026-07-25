<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, ApiError } from '$lib/api/client';
	import { logStream } from '$lib/api/live';
	import type { Coverage, RunRecord } from '$lib/api/types';
	import RunDetail from '$lib/screens/RunDetail.svelte';

	let run = $state<RunRecord | null>(null);
	let logs = $state<string[]>([]);
	let coverage = $state<Coverage | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let notFound = $state(false);
	let deciding = $state(false);
	let decideError = $state<string | null>(null);

	const id = $derived(page.params.id ?? '');

	async function load() {
		loading = true;
		error = null;
		notFound = false;
		try {
			run = await api.getRun(id);
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

	// Live log tail (023): subscribe to the run's SSE log stream; each line arrives as it is recorded and
	// the server closes the tail with an `end` event once the run is terminal. On that terminal signal we
	// re-fetch the run so its final status/timing settle. Re-subscribes when the id changes; the store's
	// teardown closes the EventSource so navigating away stops the tail.
	$effect(() => {
		const runId = id;
		if (!runId) return;
		const unsubscribe = logStream(runId).subscribe((tail) => {
			logs = tail.lines;
			if (tail.done) api.getRun(runId).then((r) => (run = r)).catch(() => {});
		});
		return unsubscribe;
	});
</script>

<RunDetail
	{run}
	{logs}
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
