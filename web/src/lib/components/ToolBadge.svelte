<script lang="ts">
	import { toolAccent } from '$lib/theme/tokens';

	let { tool }: { tool: string } = $props();

	const c = $derived(toolAccent(tool));
</script>

<span class="badge k-mono" style="color:{c}; border-color:{c}55;">{tool}</span>

<style>
	.badge {
		flex: none;
		font-size: 8.5px;
		letter-spacing: 1px;
		padding: 2px 6px;
		border-radius: 3px;
		border: 1px solid;
		text-transform: lowercase;
		white-space: nowrap;
	}
</style>
