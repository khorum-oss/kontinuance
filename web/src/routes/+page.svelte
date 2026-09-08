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
	// A run that started fine but records under a project other than the one in view. The descriptor's
	// own `project:` key wins over the project it was launched under (039), so the two can disagree — and
	// when they do the runs list simply stayed empty: the run happened, the server logged it, and nothing
	// on screen said where it went. Naming it is the difference between "broken" and "over there".
	let startedElsewhere = $state<{ id: string; project: string } | null>(null);

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
	// The window the server derived its project run-counts over; the runs fetch matches it (see `init`).
	// Undefined until the projects call returns, in which case the server applies its own list default.
	let runWindow = $state<number | undefined>(undefined);
	// Whether the project list is trustworthy yet. RUN PIPELINE promises "this runs the project you are
	// looking at", and that promise needs the list to keep it — so an unloaded or failed list must not be
	// treated as permission. Defaulting permissive here is what let a failed fetch leave the trigger
	// enabled while scoped to a project the server would not actually run.
	let projectsState = $state<'loading' | 'ready' | 'failed'>('loading');

	const unscoped = $derived(projectFilter === 'all');
	const activeProjectEntry = $derived(projectsList.find((p) => p.name === projectFilter));
	// Unscoped claims no project, so there is nothing to mislead about: the trigger runs whatever is
	// active, which is exactly what it says. Scoped, every condition below must be positively confirmed.
	const confirmed = $derived(projectsState === 'ready' && activeProjectEntry !== undefined);
	const scopedRunnable = $derived(activeProjectEntry?.runnable !== false);
	// A project can be `runnable` (has a descriptor) without being the one the server would run right now.
	const scopedActive = $derived(activeProjectEntry?.active === true);
	// Gate on all three: we know what the scope is, it has a descriptor, and it is the server's active
	// project. Anything less would run a DIFFERENT pipeline than the one on screen (the 039 footgun).
	const runnable = $derived(unscoped || (confirmed && scopedRunnable && scopedActive));
	const projectName = $derived(unscoped ? '' : projectFilter);
	// Which of the three disabled reasons applies, so the hint names the real cause. `unconfirmed` covers
	// both a failed fetch and a scope the server does not list; `loading` deliberately yields no hint at
	// all, so an ordinary page load doesn't flash alarming text before the list arrives.
	const unconfirmedReason = $derived(!unscoped && projectsState === 'failed');
	const notActiveReason = $derived(!unscoped && confirmed && scopedRunnable && !scopedActive);

	function render() {
		const all = mergeNewestFirst(byId.values());
		total = all.length;
		// Facet options come from three sources, each covering a gap the others leave:
		//   - projects represented in the loaded runs, so a project with history is always offered;
		//   - every project the server knows about, so switching away from a scope never removes it from
		//     the dropdown (that one-way door used to strand you until EXIT → picker);
		//   - the current scope itself, so the <select> always has an <option> for its own value even if
		//     the projects fetch failed and the runs carry no trace of it.
		const derived = all.map(runProject).filter((p): p is string => p !== null);
		const known = projectsList.map((p) => p.name);
		const current = projectFilter === 'all' ? [] : [projectFilter];
		projectOptions = [...new Set([...derived, ...known, ...current])].sort();
		runs = filterRuns(all, {
			query,
			status: statusFilter,
			trigger: triggerFilter,
			project: projectFilter
		}).map((r) => toRunView(r));
	}

	// Re-project when a filter changes (byId is untouched, so clearing restores the full list instantly).
	// Changing a filter also retires the "started elsewhere" notice: it describes one click's outcome
	// against one scope, and it would be stale the moment either changes.
	$effect(() => {
		query;
		statusFilter;
		triggerFilter;
		projectFilter;
		startedElsewhere = null;
		render();
	});

	async function load() {
		loading = true;
		error = null;
		try {
			for (const r of await api.listRuns(runWindow)) byId.set(r.id, r);
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
			// Reflect the just-started run immediately; the SSE stream keeps it current after this. The
			// placeholder carries the scope it was launched under — the server stamps the same project on
			// the real record — or a scoped list would filter out the very run the operator just started.
			if (id) {
				byId.set(id, { id, pipeline: '', status: 'Running', project: unscoped ? undefined : projectFilter });
			}
			render();
			await load();
			// Where did it actually land? `load` has replaced the placeholder with the server's record, so
			// this is the project the run is really filed under, not the one we assumed.
			const landed = id ? byId.get(id) : undefined;
			const project = landed ? runProject(landed) : null;
			startedElsewhere =
				!unscoped && project && project !== projectFilter ? { id, project } : null;
		} catch (e) {
			triggerError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			triggering = false;
		}
	}

	// Projects first, then runs: the projects response carries the window its run counts were computed
	// over, and the runs list loads exactly that window so a count can never advertise more runs than the
	// list can show. A failed projects fetch must NOT block the runs list — the runs fetch falls back to
	// the server's own default window — but it DOES withhold the trigger, which cannot be honest without
	// knowing which project is active.
	async function init() {
		try {
			const body = await api.getProjects();
			projectsList = body.projects;
			runWindow = body.runWindow;
			projectsState = 'ready';
		} catch {
			// The runs list still loads; only the trigger is withheld (see `runnable`).
			projectsState = 'failed';
		}
		await load();
	}

	$effect(() => {
		void init();
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
	{startedElsewhere}
	{runnable}
	{projectName}
	{notActiveReason}
	{unconfirmedReason}
	onopen={(id) => goto(`/runs/${encodeURIComponent(id)}`)}
	onretry={load}
	ontrigger={trigger}
	onquery={(v) => (query = v)}
	onstatus={(v) => (statusFilter = v)}
	ontriggerfilter={(v) => (triggerFilter = v)}
	onproject={(v) => (projectFilter = v)}
/>
