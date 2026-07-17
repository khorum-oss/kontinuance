<script lang="ts">
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';
	import { page } from '$app/state';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import Topbar from '$lib/components/Topbar.svelte';
	import Login from '$lib/components/Login.svelte';

	let { children } = $props();

	let entered = $state(false);

	const path = $derived(page.url.pathname);
	const title = $derived(titleFor(path));

	function titleFor(p: string): string {
		if (p.startsWith('/runs/')) return 'RUN DETAIL';
		if (p.startsWith('/pipeline')) return 'PIPELINE';
		if (p.startsWith('/deploy')) return 'DEPLOY';
		if (p.startsWith('/coverage')) return 'COVERAGE';
		if (p.startsWith('/config')) return 'CONFIG';
		return 'RUNS';
	}
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

{#if !entered}
	<Login oncomplete={() => (entered = true)} />
{/if}

<div class="app">
	<Sidebar active={path} onexit={() => (entered = false)} />
	<div class="main">
		<Topbar {title} />
		<div class="content">{@render children()}</div>
	</div>
</div>

<style>
	.app {
		display: flex;
		height: 100vh;
		overflow: hidden;
	}
	.main {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.content {
		flex: 1;
		min-height: 0;
		overflow: auto;
	}
</style>
