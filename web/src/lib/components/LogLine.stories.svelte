<script module lang="ts">
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import LogLine from './LogLine.svelte';

	const { Story } = defineMeta({
		title: 'Components/LogLine',
		component: LogLine,
		args: { time: '01:00:04' }
	});
</script>

<Story name="Normal" args={{ message: '> Task :core:compileKotlin' }} />
<Story name="Ok" args={{ message: 'BUILD SUCCESSFUL', tone: 'ok' }} />
<Story name="Error" args={{ message: 'integration tests: 2 failed', tone: 'error' }} />
<Story name="Muted" args={{ message: '— streaming pending —', tone: 'muted' }} />
