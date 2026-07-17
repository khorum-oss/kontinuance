<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import RunDetail from './RunDetail.svelte';
	import { sampleRunRecords } from '$lib/fixtures/runs';
	import { sampleCoverage } from '$lib/fixtures/coverage';

	const { Story } = defineMeta({
		title: 'Screens/RunDetail',
		component: RunDetail,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof RunDetail>)}
	<div style="height:100vh;">
		<RunDetail {...args} />
	</div>
{/snippet}

<Story name="Success with coverage" args={{ run: sampleRunRecords[0], coverage: sampleCoverage }} {template} />
<Story name="Failed" args={{ run: sampleRunRecords[1], coverage: sampleCoverage }} {template} />
<Story name="Not found" args={{ notFound: true }} {template} />
<Story name="Loading" args={{ loading: true }} {template} />
