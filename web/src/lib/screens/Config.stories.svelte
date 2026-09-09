<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Config from './Config.svelte';
	import { sampleConfig } from '$lib/fixtures/deploy';

	const { Story } = defineMeta({
		title: 'Screens/Config',
		component: Config,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Config>)}
	<div style="height:100vh; overflow:auto;">
		<Config {...args} />
	</div>
{/snippet}

<Story name="Populated" args={{ config: sampleConfig }} {template} />
<!-- The active project's descriptor could not be resolved (041): the reason stands in for the
     descriptor, and EDIT is withheld so an unrelated pipeline cannot be saved as its override. -->
<Story
	name="Unresolved"
	args={{
		config: {
			...sampleConfig,
			source: 'spektr',
			text: '',
			origin: 'unresolved',
			reason: "branch 'main' not found on khorum-oss/spektr"
		}
	}}
	{template}
/>
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Error" args={{ error: 'request failed: 500' }} {template} />
