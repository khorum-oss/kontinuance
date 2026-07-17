<script module lang="ts">
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import StatusDot from './StatusDot.svelte';

	const { Story } = defineMeta({
		title: 'Components/StatusDot',
		component: StatusDot,
		args: { size: 14 },
		argTypes: {
			status: {
				control: 'select',
				options: ['success', 'failed', 'running', 'pending', 'skipped', 'timedout', 'cancelled']
			}
		}
	});
</script>

<Story name="Running" args={{ status: 'running' }} />
<Story name="Success" args={{ status: 'success' }} />
<Story name="Failed" args={{ status: 'failed' }} />
<Story name="Pending" args={{ status: 'pending' }} />
<Story name="Cancelled" args={{ status: 'cancelled' }} />
