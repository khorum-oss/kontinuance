<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Topbar from './Topbar.svelte';

	const { Story } = defineMeta({
		title: 'Shell/Topbar',
		component: Topbar,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Topbar>)}
	<div style="width:1000px;">
		<Topbar {...args} />
	</div>
{/snippet}

<Story name="Runs" args={{ title: 'RUNS', runId: '—' }} {template} />
<Story name="Run detail" args={{ title: 'RUN DETAIL', runId: '#KX-2046' }} {template} />
