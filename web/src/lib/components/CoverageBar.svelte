<script lang="ts">
	import { coverageColor } from '$lib/theme/tokens';

	let { pct, height = 10 }: { pct: number; height?: number } = $props();

	const covered = $derived(Math.max(0, Math.min(100, pct)));
	const fill = $derived(coverageColor(covered));
</script>

<div class="bar" style="height:{height}px;">
	<div class="covered" style="width:{covered}%; background:{fill};"></div>
	<div class="missed" style="width:{100 - covered}%;"></div>
</div>

<style>
	.bar {
		display: flex;
		background: var(--k-inset);
		border-radius: 2px;
		overflow: hidden;
	}
	.covered,
	.missed {
		height: 100%;
	}
	.missed {
		background: rgba(248, 113, 113, 0.35);
	}
</style>
