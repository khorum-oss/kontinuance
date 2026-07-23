<script lang="ts">
	import { BRIGHTNESS_MAX, BRIGHTNESS_MIN, type ThemeMode } from '$lib/theme/preferences';

	let {
		mode = 'dark',
		brightness = 1,
		ontoggle,
		onbrightness
	}: {
		mode?: ThemeMode;
		brightness?: number;
		ontoggle?: () => void;
		onbrightness?: (value: number) => void;
	} = $props();
</script>

<div class="controls">
	<input
		class="bright"
		type="range"
		min={BRIGHTNESS_MIN}
		max={BRIGHTNESS_MAX}
		step="0.05"
		value={brightness}
		aria-label="brightness"
		title="brightness"
		oninput={(e) => onbrightness?.(parseFloat(e.currentTarget.value))}
	/>
	<button
		class="k-mono toggle"
		aria-label="toggle light or dark theme"
		aria-pressed={mode === 'light'}
		onclick={() => ontoggle?.()}
	>
		{mode === 'dark' ? '☾ DARK' : '☀ LIGHT'}
	</button>
</div>

<style>
	.controls {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.bright {
		width: 84px;
		accent-color: var(--k-teal);
		cursor: pointer;
	}
	.toggle {
		font-size: 10px;
		letter-spacing: 1.5px;
		color: var(--k-muted-2);
		background: none;
		border: 1px solid var(--k-border);
		border-radius: 4px;
		padding: 5px 10px;
		cursor: pointer;
	}
	.toggle:hover {
		color: var(--k-teal);
		border-color: rgba(94, 234, 212, 0.4);
	}
</style>
