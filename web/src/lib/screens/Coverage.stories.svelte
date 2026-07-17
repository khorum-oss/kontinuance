<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Coverage from './Coverage.svelte';
	import { sampleCoverage } from '$lib/fixtures/coverage';

	const { Story } = defineMeta({
		title: 'Screens/Coverage',
		component: Coverage,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Coverage>)}
	<div style="height:100vh; overflow:auto;">
		<Coverage {...args} />
	</div>
{/snippet}

<Story name="Populated" args={{ coverage: sampleCoverage }} {template} />
<Story name="Loading" args={{ loading: true }} {template} />
<Story name="Error" args={{ error: 'request failed: 500' }} {template} />
