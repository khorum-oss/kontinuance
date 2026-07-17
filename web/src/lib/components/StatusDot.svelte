<script lang="ts">
	import { statusColor, statusPulses, type Status } from '$lib/theme/tokens';

	let { status, size = 8 }: { status: Status; size?: number } = $props();

	const c = $derived(statusColor(status));
	const anim = $derived(statusPulses(status) ? 'kpulsesoft 3.6s ease-in-out infinite' : 'none');
</script>

<span class="dot" style="width:{size}px; height:{size}px; background:{c}; animation:{anim};"></span>

<style>
	.dot {
		display: inline-block;
		border-radius: 50%;
		flex: none;
	}
</style>
