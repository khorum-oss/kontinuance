import { describe, expect, it } from 'vitest';
import {
	BRIGHTNESS_DEFAULT,
	BRIGHTNESS_MAX,
	BRIGHTNESS_MIN,
	clampBrightness,
	otherMode,
	resolveMode
} from './preferences';

describe('clampBrightness', () => {
	it('bounds values to the allowed range', () => {
		expect(clampBrightness(5)).toBe(BRIGHTNESS_MAX);
		expect(clampBrightness(0.1)).toBe(BRIGHTNESS_MIN);
		expect(clampBrightness(1)).toBe(1);
	});

	it('falls back to the default for invalid input', () => {
		expect(clampBrightness(NaN)).toBe(BRIGHTNESS_DEFAULT);
		expect(clampBrightness(Infinity)).toBe(BRIGHTNESS_DEFAULT);
	});
});

describe('otherMode', () => {
	it('flips the mode', () => {
		expect(otherMode('dark')).toBe('light');
		expect(otherMode('light')).toBe('dark');
	});
});

describe('resolveMode', () => {
	it('prefers a saved choice', () => {
		expect(resolveMode('light', false)).toBe('light');
		expect(resolveMode('dark', true)).toBe('dark');
	});

	it('falls back to the OS preference when unset or invalid', () => {
		expect(resolveMode(null, true)).toBe('light');
		expect(resolveMode(undefined, false)).toBe('dark');
		expect(resolveMode('garbage', true)).toBe('light');
	});

	it('defaults to dark with no signal', () => {
		expect(resolveMode(null, false)).toBe('dark');
	});
});
