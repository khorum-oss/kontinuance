<script lang="ts">
	import { api, ApiError } from '$lib/api/client';
	import type { ConnectSourceRequest, RunRecord, SourceStatus } from '$lib/api/types';
	import SourceScreen from '$lib/screens/Source.svelte';

	let source = $state<SourceStatus | null>(null);
	let runs = $state<RunRecord[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let busy = $state(false);
	let connectError = $state<string | null>(null);

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

	// A rejected connect keeps the screen as it was and shows the server's message against the form —
	// re-rendering the whole screen into an error state would discard what the operator just typed.
	async function connect(request: ConnectSourceRequest) {
		busy = true;
		connectError = null;
		try {
			source = await api.connectSource(request);
			runs = (await api.listRuns()).filter(
				(r) => r.trigger != null && GITHUB_TRIGGERS.has(r.trigger)
			);
		} catch (e) {
			connectError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			busy = false;
		}
	}

	async function disconnect(forget: boolean) {
		busy = true;
		connectError = null;
		try {
			source = await api.disconnectSource(forget);
		} catch (e) {
			connectError = e instanceof ApiError ? e.message : (e as Error).message;
		} finally {
			busy = false;
		}
	}

	$effect(() => {
		load();
	});
</script>

<SourceScreen
	{source}
	{runs}
	{loading}
	{error}
	{busy}
	{connectError}
	onretry={load}
	onconnect={connect}
	ondisconnect={disconnect}
/>
