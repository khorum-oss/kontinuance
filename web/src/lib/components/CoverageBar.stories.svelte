<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import CoverageBar from './CoverageBar.svelte';

	const { Story } = defineMeta({
		title: 'Components/CoverageBar',
		component: CoverageBar
	});
</script>

{#snippet template(args: ComponentProps<typeof CoverageBar>)}
	<div style="width:320px;">
		<CoverageBar {...args} />
	</div>
{/snippet}

<Story name="Healthy" args={{ pct: 91 }} {template} />
<Story name="Warning" args={{ pct: 68 }} {template} />
<Story name="Poor" args={{ pct: 44 }} {template} />
