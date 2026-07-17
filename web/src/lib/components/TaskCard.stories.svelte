<script module lang="ts">
	import type { ComponentProps } from 'svelte';
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import type { PipelineTask } from '$lib/api/types';
	import TaskCard from './TaskCard.svelte';

	const { Story } = defineMeta({
		title: 'Components/TaskCard',
		component: TaskCard,
		parameters: { layout: 'padded' }
	});

	const base: Omit<PipelineTask, 'status' | 'progress'> = {
		id: 't1',
		name: ':core assemble',
		tool: 'gradle',
		deps: []
	};
</script>

{#snippet template(args: ComponentProps<typeof TaskCard>)}
	<div style="width:212px;">
		<TaskCard {...args} />
	</div>
{/snippet}

<Story name="Running" args={{ task: { ...base, status: 'running', progress: 62 } }} {template} />
<Story name="Success" args={{ task: { ...base, status: 'success', progress: 100 } }} {template} />
<Story
	name="Pending with deps"
	args={{ task: { ...base, name: 'unit tests', status: 'pending', progress: 0, deps: ['core', 'api'] } }}
	{template}
/>
<Story name="Active" args={{ task: { ...base, status: 'running', progress: 40 }, active: true }} {template} />
<Story name="Dimmed" args={{ task: { ...base, status: 'pending', progress: 0 }, dim: true }} {template} />
