<script module lang="ts">
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import ToolBadge from './ToolBadge.svelte';

	const { Story } = defineMeta({
		title: 'Components/ToolBadge',
		component: ToolBadge,
		argTypes: {
			tool: {
				control: 'select',
				options: ['git', 'gradle', 'maven', 'env', 'cache', 'lint', 'bun', 'oci', 'nexus', 'argo']
			}
		}
	});
</script>

<Story name="Gradle" args={{ tool: 'gradle' }} />
<Story name="Git" args={{ tool: 'git' }} />
<Story name="Maven" args={{ tool: 'maven' }} />
<Story name="Argo" args={{ tool: 'argo' }} />
