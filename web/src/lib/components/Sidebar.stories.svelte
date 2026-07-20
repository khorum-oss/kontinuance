<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Sidebar from './Sidebar.svelte';

	const { Story } = defineMeta({
		title: 'Shell/Sidebar',
		component: Sidebar,
		parameters: { layout: 'fullscreen' }
	});
</script>

{#snippet template(args: ComponentProps<typeof Sidebar>)}
	<div style="height:100vh; display:flex;">
		<Sidebar {...args} />
	</div>
{/snippet}

<Story name="Runs active" args={{ active: '/', operator: 'mkuraja' }} {template} />
<Story name="Deploy active" args={{ active: '/deploy' }} {template} />
<Story name="Coverage active" args={{ active: '/coverage' }} {template} />
