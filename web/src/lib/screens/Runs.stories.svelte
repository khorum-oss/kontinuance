<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Runs from './Runs.svelte';
	import { sampleRunViews } from '$lib/fixtures/runs';

	const { Story } = defineMeta({
		title: 'Screens/Runs',
		component: Runs,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Runs>)}
	<div style="height:100vh; overflow:auto;">
		<Runs {...args} />
	</div>
{/snippet}

<Story name="Populated" args={{ runs: sampleRunViews }} {template} />
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Empty" args={{ runs: [] }} {template} />
<Story name="Error" args={{ error: 'cannot reach the server (Failed to fetch)' }} {template} />
<Story name="Degraded" args={{ runs: sampleRunViews, degraded: true }} {template} />
