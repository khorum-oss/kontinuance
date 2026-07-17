<script lang="ts">
	import { page } from '$app/state';
	import Placeholder from '$lib/screens/Placeholder.svelte';
</script>

<Placeholder screen={'RUN ' + page.params.id} />
