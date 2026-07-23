<script lang="ts">
	import ThemeControls from '$lib/components/ThemeControls.svelte';
	import type { ThemeMode } from '$lib/theme/preferences';

	let {
		title = 'RUNS',
		runId = '—',
		mode = 'dark',
		brightness = 1,
		ontoggletheme,
		onbrightness
	}: {
		title?: string;
		runId?: string;
		mode?: ThemeMode;
		brightness?: number;
		ontoggletheme?: () => void;
		onbrightness?: (value: number) => void;
	} = $props();

	let clock = $state(formatClock());

	function formatClock(): string {
		const d = new Date();
		const p = (n: number) => n.toString().padStart(2, '0');
		return `${p(d.getUTCHours())}:${p(d.getUTCMinutes())}:${p(d.getUTCSeconds())}Z`;
	}

	$effect(() => {
		const t = setInterval(() => (clock = formatClock()), 1000);
		return () => clearInterval(t);
	});
</script>

<div class="topbar">
	<div class="k-mono title">{title}</div>
	<div class="right">
		<div class="k-mono item">MISSION CLOCK <span class="teal">{clock}</span></div>
		<div class="k-mono item">RUN <span class="val">{runId}</span></div>
		<ThemeControls {mode} {brightness} ontoggle={ontoggletheme} {onbrightness} />
	</div>
</div>

<style>
	.topbar {
		flex: none;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 14px 26px;
		border-bottom: 1px solid var(--k-border-soft);
	}
	.title {
		font-size: 11px;
		letter-spacing: 2px;
		color: var(--k-muted-2);
	}
	.right {
		display: flex;
		align-items: center;
		gap: 22px;
	}
	.item {
		font-size: 11px;
		letter-spacing: 1px;
		color: var(--k-muted-4);
	}
	.teal {
		color: var(--k-teal);
	}
	.val {
		color: var(--k-text);
	}
</style>
