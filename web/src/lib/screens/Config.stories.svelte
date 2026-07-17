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
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Error" args={{ error: 'request failed: 500' }} {template} />
