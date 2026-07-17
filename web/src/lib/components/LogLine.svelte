<script lang="ts">
	import { color } from '$lib/theme/tokens';

	let {
		time = '',
		message,
		tone = 'normal'
	}: { time?: string; message: string; tone?: 'normal' | 'muted' | 'error' | 'ok' } = $props();

	const c = $derived(
		tone === 'error'
			? color.fail
			: tone === 'ok'
				? color.ok
				: tone === 'muted'
					? color.muted4
					: color.muted
	);
</script>

<div class="line k-mono">
	<span class="t">{time}</span>
	<span class="m" style="color:{c};">{message}</span>
</div>

<style>
	.line {
		display: flex;
		gap: 14px;
		font-size: 11.5px;
		line-height: 1.75;
	}
	.t {
		flex: none;
		color: var(--k-faint);
	}
	.m {
		white-space: pre-wrap;
	}
</style>
