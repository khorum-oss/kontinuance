<script lang="ts">
	import { color } from '$lib/theme/tokens';

	let {
		value = 0,
		indeterminate = false,
		active = false,
		fill = `linear-gradient(90deg, ${color.teal2}, ${color.teal})`,
		height = 3
	}: {
		value?: number;
		indeterminate?: boolean;
		active?: boolean;
		fill?: string;
		height?: number;
	} = $props();

	const w = $derived(indeterminate ? 42 : Math.max(0, Math.min(100, value)));

	// The live segment sits just past the filled portion: the work in flight, which a step-counted bar
	// cannot measure. Without it a long step reads as a frozen bar rather than a running one. It never
	// runs past the end of the track, and never appears when the whole bar is already animating.
	const live = $derived(!indeterminate && active && w < 100);
	const liveWidth = $derived(Math.min(18, 100 - w));
</script>

<div class="track" style="height:{height}px;">
	<div
		class="fill"
		class:indet={indeterminate}
		style="width:{w}%; background:{fill};"
	></div>
	{#if live}
		<div class="live" style="left:{w}%; width:{liveWidth}%; background:{fill};"></div>
	{/if}
</div>

<style>
	.track {
		position: relative;
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
	.live {
		position: absolute;
		top: 0;
		bottom: 0;
		border-radius: 2px;
		opacity: 0.4;
		animation: kpulse 1.4s ease-in-out infinite;
	}
	@keyframes kpulse {
		0%,
		100% {
			opacity: 0.15;
		}
		50% {
			opacity: 0.5;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.live {
			animation: none;
			opacity: 0.3;
		}
		.indet {
			animation: none;
		}
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
