<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Pipeline from './Pipeline.svelte';
	import { samplePipeline } from '$lib/fixtures/pipeline';

	const { Story } = defineMeta({
		title: 'Screens/Pipeline',
		component: Pipeline,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Pipeline>)}
	<div style="height:100vh;">
		<Pipeline {...args} />
	</div>
{/snippet}

<Story name="Populated" args={{ pipeline: samplePipeline }} {template} />
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Error" args={{ error: 'request failed: 500' }} {template} />
