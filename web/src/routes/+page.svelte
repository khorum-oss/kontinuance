<script lang="ts">
	import { goto } from '$app/navigation';
	import { api, ApiError } from '$lib/api/client';
	import { runStream } from '$lib/api/live';
	import { mergeNewestFirst, toRunView, type RunView } from '$lib/api/present';
	import type { RunRecord } from '$lib/api/types';
	import Runs from '$lib/screens/Runs.svelte';

	// Records keyed by id, seeded by the initial fetch and kept live by the SSE stream.
	const byId = new Map<string, RunRecord>();

	let runs = $state<RunView[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let degraded = $state(false);
	let triggering = $state(false);
	let triggerError = $state<string | null>(null);

	function render() {
		runs = mergeNewestFirst(byId.values()).map((r) => toRunView(r));
	}

	async function load() {
		loading = true;
		error = null;
		try {
			for (const r of await api.listRuns(100)) byId.set(r.id, r);
			render();
		} catch (e) {
			error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			loading = false;
		}
	}

	async function trigger() {
		triggering = true;
		triggerError = null;
		try {
			const id = await api.triggerRun();
			// Reflect the just-started run immediately; the SSE stream keeps it current after this.
			if (id) byId.set(id, { id, pipeline: '', status: 'Running' });
			render();
			await load();
		} catch (e) {
			triggerError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			triggering = false;
		}
	}

	$effect(() => {
		load();
	});

	// Live updates: merge streamed records and reflect the connection's degraded state.
	$effect(() => {
		const unsubscribe = runStream().subscribe((s) => {
			degraded = s.degraded;
			if (s.runs.length) {
				for (const r of s.runs) byId.set(r.id, r);
				render();
			}
		});
		return unsubscribe;
	});
</script>

<Runs
	{runs}
	{loading}
	{error}
	{degraded}
	{triggering}
	{triggerError}
	onopen={(id) => goto(`/runs/${encodeURIComponent(id)}`)}
	onretry={load}
	ontrigger={trigger}
/>
