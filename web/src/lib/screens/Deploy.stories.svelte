<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Deploy from './Deploy.svelte';
	import { sampleDeploy } from '$lib/fixtures/deploy';

	const { Story } = defineMeta({
		title: 'Screens/Deploy',
		component: Deploy,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Deploy>)}
	<div style="height:100vh; overflow:auto;">
		<Deploy {...args} />
	</div>
{/snippet}

<Story name="Populated" args={{ deploy: sampleDeploy }} {template} />
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Error" args={{ error: 'request failed: 500' }} {template} />
