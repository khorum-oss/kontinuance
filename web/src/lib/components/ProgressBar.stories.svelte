<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import ProgressBar from './ProgressBar.svelte';

	const { Story } = defineMeta({
		title: 'Components/ProgressBar',
		component: ProgressBar
	});
</script>

{#snippet template(args: ComponentProps<typeof ProgressBar>)}
	<div style="width:320px;">
		<ProgressBar {...args} />
	</div>
{/snippet}

<Story name="Half" args={{ value: 50 }} {template} />
<Story name="Full" args={{ value: 100 }} {template} />
<Story name="Indeterminate" args={{ indeterminate: true }} {template} />
