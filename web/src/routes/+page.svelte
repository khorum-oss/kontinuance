<script lang="ts">
	import { getContext } from 'svelte';
	import { goto } from '$app/navigation';
	import { api, ApiError } from '$lib/api/client';
	import { runStream } from '$lib/api/live';
	import { filterRuns, mergeNewestFirst, runProject, toRunView, type RunView } from '$lib/api/present';
	import type { Project, RunRecord } from '$lib/api/types';
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
	// the full project registry) so the list never offers a scope that would trivially empty the table —
	// plus the currently-active scope even when it matches zero loaded runs (e.g. a picker selection with
	// no history yet), so the <select> always has an <option> for its own value and never renders blank.
	let projectOptions = $state<string[]>([]);

	// The registered project list (032/039), fetched once so the trigger control can tell a registered
	// project (runnable) from a derived one (no descriptor — nothing for RUN PIPELINE to run). Reactive to
	// `projectFilter` below so the dropdown, not just the entry picker, keeps this correct.
	let projectsList = $state<Project[]>([]);
	// Unscoped view keeps today's behavior (trigger always enabled); a scope not yet in the loaded list
	// (e.g. still fetching) also defaults permissive rather than punishing a load race with a false negative.
	const activeProjectEntry = $derived(projectsList.find((p) => p.name === projectFilter));
	const scopedRunnable = $derived(projectFilter === 'all' ? true : (activeProjectEntry?.runnable ?? true));
	// The server's currently-active project (the one RUN PIPELINE actually runs) — a project can be
	// `runnable` (has a descriptor) without being the one the server would run right now. Same fail-open
	// default as above: while the projects list hasn't loaded, don't punish the race with a false negative.
	const scopedActive = $derived(projectFilter === 'all' ? true : (activeProjectEntry?.active ?? true));
	// Gate the trigger on BOTH: a scope with no descriptor can't run at all, and a scope that isn't the
	// server's active project would run a DIFFERENT project's pipeline than the one on screen (039 footgun).
	const runnable = $derived(scopedRunnable && scopedActive);
	const projectName = $derived(projectFilter === 'all' ? '' : projectFilter);
	// Distinguishes the two disabled reasons so the hint names the right one.
	const notActiveReason = $derived(scopedRunnable && !scopedActive);

	function render() {
		const all = mergeNewestFirst(byId.values());
		total = all.length;
		const derived = all.map(runProject).filter((p): p is string => p !== null);
		const withActiveScope = projectFilter === 'all' ? derived : [...derived, projectFilter];
		projectOptions = [...new Set(withActiveScope)].sort();
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

	// Best-effort: a failed fetch here shouldn't block the runs list — `runnable` just stays permissive
	// (see the default above) until it succeeds.
	$effect(() => {
		api
			.getProjects()
			.then((body) => (projectsList = body.projects))
			.catch(() => {});
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
	{runnable}
	{projectName}
	{notActiveReason}
	onopen={(id) => goto(`/runs/${encodeURIComponent(id)}`)}
	onretry={load}
	ontrigger={trigger}
	onquery={(v) => (query = v)}
	onstatus={(v) => (statusFilter = v)}
	ontriggerfilter={(v) => (triggerFilter = v)}
	onproject={(v) => (projectFilter = v)}
/>
