// Appearance preferences (019): the pure, DOM-free logic behind the light/dark theme and the brightness
// dimmer. The layout owns the reactive state and the DOM/`localStorage` side effects; these helpers just
// clamp and resolve, so they are trivially unit-testable.

export type ThemeMode = 'light' | 'dark';

export const MODE_KEY = 'knt-theme';
export const BRIGHTNESS_KEY = 'knt-brightness';

/** Brightness is a bounded global multiplier around normal (never fully black or blinding). */
export const BRIGHTNESS_MIN = 0.7;
export const BRIGHTNESS_MAX = 1.2;
export const BRIGHTNESS_DEFAULT = 1;

/** Clamp a (possibly NaN/invalid) brightness to the allowed range, falling back to the default. */
export function clampBrightness(value: number): number {
	if (!Number.isFinite(value)) return BRIGHTNESS_DEFAULT;
	return Math.min(BRIGHTNESS_MAX, Math.max(BRIGHTNESS_MIN, value));
}

/** The other mode. */
export function otherMode(mode: ThemeMode): ThemeMode {
	return mode === 'dark' ? 'light' : 'dark';
}

/**
 * Resolve the initial theme: a saved choice wins; otherwise follow the OS `prefers-color-scheme`; otherwise
 * default to dark.
 */
export function resolveMode(stored: string | null | undefined, systemPrefersLight: boolean): ThemeMode {
	if (stored === 'light' || stored === 'dark') return stored;
	return systemPrefersLight ? 'light' : 'dark';
}
