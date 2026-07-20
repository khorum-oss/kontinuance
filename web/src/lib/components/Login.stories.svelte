<script module lang="ts">
	import { defineMeta } from '@storybook/addon-svelte-csf';
	import Login from './Login.svelte';

	const { Story } = defineMeta({
		title: 'Shell/Login',
		component: Login,
		parameters: { layout: 'fullscreen' }
	});
</script>

<Story name="Sign in" args={{ requireSignIn: true }} />
<Story name="Project (signed in)" args={{ requireSignIn: false, operator: 'mkuraja' }} />
