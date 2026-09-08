<script lang="ts">
	import { getContext } from 'svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { api, ApiError } from '$lib/api/client';
	import { runStream } from '$lib/api/live';
	import { mergeNewestFirst, runProject } from '$lib/api/present';
	import type { Pipeline, RunRecord } from '$lib/api/types';
	import PipelineScreen from '$lib/screens/Pipeline.svelte';

	// The project activated in the entry picker (039), read from the layout via context. Without it this
	// screen showed the newest run on the server regardless of scope — so a dashboard focused on one
	// project could show another project's pipeline, which is what made the screen look unrelated to
	// everything else. 'all' means unscoped.
	const activeProject = getContext<{ current: string }>('activeProject');
	const scope = $derived(activeProject?.current ?? 'all');

	// An explicit `?run=` pins this screen to one run — the run detail links here so the two views agree
	// on which run they describe. Without it the screen follows the newest run in the active scope.
	const pinned = $derived(page.url.searchParams.get('run'));

	let pipeline = $state<Pipeline | null>(null);
	let run = $state<RunRecord | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	// The same run window the runs list and the project picker use, so "no runs for this project" here means
	// the same thing it means there. Undefined until the projects call answers (or if it fails), in which
	// case the server applies its own list default. Deliberately NOT reactive — nothing renders it, and a
	// tracked write inside the load effect would re-run that effect for no reason.
	let runWindow: number | undefined;
	let windowRead = false;

	/** The newest run in the current scope, or null when the scope has no runs in that window. */
	async function newestInScope(): Promise<RunRecord | null> {
		const runs = mergeNewestFirst(await api.listRuns(runWindow));
		if (scope === 'all') return runs[0] ?? null;
		return runs.find((r) => runProject(r) === scope) ?? null;
	}

	// `silent` refreshes in place: a stream-driven update must not blank the flow back to "loading…" every
	// time a step lands.
	async function load(silent = false) {
		if (!silent) loading = true;
		error = null;
		try {
			const target = pinned ? await api.getRun(pinned) : await newestInScope();
			run = target;
			// No run to describe is a state, not a failure: say so instead of rendering a pipeline that
			// belongs to nothing.
			pipeline = target ? await api.getPipeline(target.id) : null;
		} catch (e) {
			error = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			loading = false;
		}
	}

	// Reload when the pinned run or the active scope changes. The window is read once, before the first
	// load; a failure there only means the server's default window applies.
	$effect(() => {
		pinned;
		scope;
		void (async () => {
			if (!windowRead) {
				windowRead = true;
				runWindow = await api
					.getProjects()
					.then((body) => body.runWindow)
					.catch(() => undefined);
			}
			await load();
		})();
	});

	// Keep the view current while the run executes: the trigger records the pipeline's declared stages up
	// front and rewrites them with real per-step results when the run settles, so a streamed change to the
	// run on screen is the signal to re-read its breakdown. An unpinned view also follows a newer run.
	//
	// The effect depends only on `pinned`/`scope` and compares a signature of the record it cares about, so
	// the reload it triggers cannot re-arm the subscription and loop.
	$effect(() => {
		const pin = pinned;
		const inScope = scope;
		let seen = '';
		const unsubscribe = runStream().subscribe((s) => {
			if (!s.runs.length) return;
			const relevant = pin
				? s.runs.find((r) => r.id === pin)
				: mergeNewestFirst(s.runs).find((r) => inScope === 'all' || runProject(r) === inScope);
			if (!relevant) return;
			const signature = `${relevant.id}:${relevant.status}`;
			if (signature === seen) return;
			seen = signature;
			void load(true);
		});
		return unsubscribe;
	});
</script>

<PipelineScreen
	{pipeline}
	{run}
	{scope}
	{loading}
	{error}
	onretry={load}
	onopenrun={(id) => goto(`/runs/${encodeURIComponent(id)}`)}
/>
