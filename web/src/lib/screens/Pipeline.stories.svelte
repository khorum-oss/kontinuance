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

<Story name="Populated" args={{ pipeline: samplePipeline, scope: 'kontinuance' }} {template} />
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Error" args={{ error: 'request failed: 500' }} {template} />
<!-- A known run that recorded no per-step breakdown: an explicit note, never a stand-in pipeline. -->
<Story
	name="No stages recorded"
	args={{ pipeline: { runId: 'run-old', stages: [] }, scope: 'kontinuance' }}
	{template}
/>
<!-- The active project has no runs at all — the screen says so instead of showing someone else's run. -->
<Story name="No runs in scope" args={{ pipeline: null, scope: 'kontinuance' }} {template} />
