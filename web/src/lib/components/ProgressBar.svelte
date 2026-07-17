<script lang="ts">
	import { color } from '$lib/theme/tokens';

	let {
		value = 0,
		indeterminate = false,
		fill = `linear-gradient(90deg, ${color.teal2}, ${color.teal})`,
		height = 3
	}: { value?: number; indeterminate?: boolean; fill?: string; height?: number } = $props();

	const w = $derived(indeterminate ? 42 : Math.max(0, Math.min(100, value)));
</script>

<div class="track" style="height:{height}px;">
	<div
		class="fill"
		class:indet={indeterminate}
		style="width:{w}%; background:{fill};"
	></div>
</div>

<style>
	.track {
		background: var(--k-inset);
		border-radius: 2px;
		overflow: hidden;
	}
	.fill {
		height: 100%;
		border-radius: 2px;
		transition: width 0.4s;
	}
	.indet {
		animation: kslide 1.3s ease-in-out infinite;
	}
	@keyframes kslide {
		0% {
			margin-left: -42%;
		}
		100% {
			margin-left: 100%;
		}
	}
</style>
