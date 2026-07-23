<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import ThemeControls from './ThemeControls.svelte';

	const { Story } = defineMeta({
		title: 'Shell/ThemeControls',
		component: ThemeControls
	});
</script>

{#snippet template(args: ComponentProps<typeof ThemeControls>)}
	<div style="padding:16px; display:flex;">
		<ThemeControls {...args} />
	</div>
{/snippet}

<Story name="Dark" args={{ mode: 'dark', brightness: 1 }} {template} />
<Story name="Light" args={{ mode: 'light', brightness: 1.1 }} {template} />
