<script lang="ts">
	import { getContext } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, ApiError } from '$lib/api/client';
	import { runStream } from '$lib/api/live';
	import { filterRuns, mergeNewestFirst, runProject, toRunView, type RunView } from '$lib/api/present';
	import type { RunRecord } from '$lib/api/types';
	import Runs from '$lib/screens/Runs.svelte';

	// The project activated in the entry picker (039), read from the layout via context — see
	// +layout.svelte for why this isn't a prop.
	const activeProject = getContext<{ current: string }>('activeProject');

	// Records keyed by id, seeded by the initial fetch and kept live by the SSE stream.
	const byId = new Map<string, RunRecord>();

	let runs = $state<RunView[]>([]);
	let total = $state(0);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let degraded = $state(false);
	let triggering = $state(false);
	let triggerError = $state<string | null>(null);

	// Runs-list filters (037) — a projection over the live set; byId stays the untouched source of truth.
	let query = $state('');
	let statusFilter = $state('all');
	let triggerFilter = $state('all');
	// Seeded from the picker's activation (039) so choosing a project lands on a scoped list; 'all' is the
	// explicit escape hatch back to the unscoped view.
	let projectFilter = $state(activeProject?.current ?? 'all');
	// Options for the project facet: every project actually represented in the loaded runs, derived (not
	// the full project registry) so the list never offers a scope that would trivially empty the table.
	let projectOptions = $state<string[]>([]);

	function render() {
		const all = mergeNewestFirst(byId.values());
		total = all.length;
		projectOptions = [...new Set(all.map(runProject).filter((p): p is string => p !== null))].sort();
		runs = filterRuns(all, {
			query,
			status: statusFilter,
			trigger: triggerFilter,
			project: projectFilter
		}).map((r) => toRunView(r));
	}

	// Re-project when a filter changes (byId is untouched, so clearing restores the full list instantly).
	$effect(() => {
		query;
		statusFilter;
		triggerFilter;
		projectFilter;
		render();
	});

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
	{total}
	{query}
	status={statusFilter}
	trigger={triggerFilter}
	project={projectFilter}
	projects={projectOptions}
	{loading}
	{error}
	{degraded}
	{triggering}
	{triggerError}
	onopen={(id) => goto(`/runs/${encodeURIComponent(id)}`)}
	onretry={load}
	ontrigger={trigger}
	onquery={(v) => (query = v)}
	onstatus={(v) => (statusFilter = v)}
	ontriggerfilter={(v) => (triggerFilter = v)}
	onproject={(v) => (projectFilter = v)}
/>
