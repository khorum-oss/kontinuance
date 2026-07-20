<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import favicon from '$lib/assets/favicon.svg';
	import { page } from '$app/state';
	import { api } from '$lib/api/client';
	import type { Session } from '$lib/api/types';
	import Sidebar from '$lib/components/Sidebar.svelte';
	import Topbar from '$lib/components/Topbar.svelte';
	import Login from '$lib/components/Login.svelte';

	let { children } = $props();

	// The shell is a small state machine driven by the server's session (016):
	//   loading → (signin | project) → app;  EXIT → project;  sign-out → signin.
	// The app shell — and therefore every runs/stream/detail call — mounts only in `app`, so an enforced
	// deployment never fires unauthenticated requests behind the sign-in screen (FR-004).
	type View = 'loading' | 'signin' | 'project' | 'app';
	let view = $state<View>('loading');
	let session = $state<Session>({ authenticated: false, authRequired: false });

	const requireSignIn = $derived(session.authRequired && !session.authenticated);
	const operator = $derived(session.username ?? 'operator');

	const path = $derived(page.url.pathname);
	const title = $derived(titleFor(path));

	onMount(async () => {
		try {
			session = await api.me();
		} catch {
			// Server unreachable at load: don't hard-lock — assume open mode and let the app's own error
			// states surface the unreachable server once the operator enters.
			session = { authenticated: false, authRequired: false };
		}
		view = requireSignIn ? 'signin' : 'project';
	});

	function onAuthenticated(username: string) {
		session = { authenticated: true, authRequired: true, username };
	}

	function onExit() {
		// Return to the project view but keep the session (FR-006) — no re-authentication to re-enter.
		view = 'project';
	}

	async function onSignOut() {
		await api.logout();
		session = { authenticated: false, authRequired: session.authRequired, username: undefined };
		view = session.authRequired ? 'signin' : 'project';
	}

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

{#if view === 'signin' || view === 'project'}
	<Login
		{requireSignIn}
		operator={session.username ?? ''}
		onauthenticated={onAuthenticated}
		oncomplete={() => (view = 'app')}
		onsignout={onSignOut}
	/>
{:else if view === 'app'}
	<div class="app">
		<Sidebar active={path} {operator} onexit={onExit} />
		<div class="main">
			<Topbar {title} />
			<div class="content">{@render children()}</div>
		</div>
	</div>
{/if}

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
