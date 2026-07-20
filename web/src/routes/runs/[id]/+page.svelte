<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, ApiError } from '$lib/api/client';
	import type { Coverage, RunRecord } from '$lib/api/types';
	import { normalizeStatus } from '$lib/theme/tokens';
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
	// While the run is active its output is still accumulating; the page re-fetches until it is terminal.
	const active = $derived.by(() => {
		if (!run) return false;
		const s = normalizeStatus(run.status);
		return s === 'running' || s === 'waiting' || s === 'pending';
	});

	async function load() {
		loading = true;
		error = null;
		notFound = false;
		try {
			run = await api.getRun(id);
			logs = await api.getRunLogs(id).catch(() => []);
			// coverage is a best-effort sidebar; a failure there must not fail the run view
			coverage = await api.getCoverage().catch(() => null);
		} catch (e) {
			if (e instanceof ApiError && e.status === 404) notFound = true;
			else error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			loading = false;
		}
	}

	// A lightweight refresh used while polling — updates the run + log without the full-screen loading state.
	async function refresh() {
		try {
			run = await api.getRun(id);
			logs = await api.getRunLogs(id).catch(() => logs);
		} catch {
			// keep the last good view; a transient failure shouldn't clobber the screen
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

	// Near-live: while the run is active, re-fetch its log (and status) on a short interval; the effect
	// tears the interval down as soon as the run reaches a terminal state (active → false).
	$effect(() => {
		if (!active) return;
		const timer = setInterval(refresh, 1500);
		return () => clearInterval(timer);
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
