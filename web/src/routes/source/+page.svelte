<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { RunRecord, SourceStatus } from '$lib/api/types';
	import SourceScreen from '$lib/screens/Source.svelte';

	let source = $state<SourceStatus | null>(null);
	let runs = $state<RunRecord[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	// Event-source runs carry an uppercase trigger kind; UI-triggered runs use lowercase "manual".
	const GITHUB_TRIGGERS = new Set(['PULL_REQUEST', 'PUSH', 'MANUAL']);

	async function load() {
		loading = true;
		error = null;
		try {
			const [status, allRuns] = await Promise.all([api.getSource(), api.listRuns()]);
			source = status;
			runs = allRuns.filter((r) => r.trigger != null && GITHUB_TRIGGERS.has(r.trigger));
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

<SourceScreen {source} {runs} {loading} {error} onretry={load} />
