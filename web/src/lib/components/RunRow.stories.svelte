<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import RunRow from './RunRow.svelte';
	import { sampleRunViews } from '$lib/fixtures/runs';

	const { Story } = defineMeta({
		title: 'Components/RunRow',
		component: RunRow,
		parameters: { layout: 'padded' }
	});
</script>

{#snippet template(args: ComponentProps<typeof RunRow>)}
	<div style="width:940px;">
		<RunRow {...args} />
	</div>
{/snippet}

<Story name="Running" args={{ run: sampleRunViews[0] }} {template} />
<Story name="Success" args={{ run: sampleRunViews[1] }} {template} />
<Story name="Failed" args={{ run: sampleRunViews[2] }} {template} />
<Story name="Cancelled" args={{ run: sampleRunViews[4] }} {template} />
