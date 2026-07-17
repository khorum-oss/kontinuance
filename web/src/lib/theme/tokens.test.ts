import { describe, expect, it } from 'vitest';
import { color, coverageColor, normalizeStatus, statusColor, statusPulses, toolAccent } from './tokens';

describe('normalizeStatus', () => {
	it('maps engine status strings to the canonical vocabulary', () => {
		expect(normalizeStatus('Success')).toBe('success');
		expect(normalizeStatus('Failed(step, reason)')).toBe('failed');
		expect(normalizeStatus('Running')).toBe('running');
		expect(normalizeStatus('TimedOut')).toBe('timedout');
		expect(normalizeStatus('Cancelled')).toBe('cancelled');
		expect(normalizeStatus('Skipped')).toBe('skipped');
		expect(normalizeStatus('Queued')).toBe('pending');
		expect(normalizeStatus('WaitingOnApproval')).toBe('pending');
	});

	it('defaults unknown / empty input to pending', () => {
		expect(normalizeStatus(undefined)).toBe('pending');
		expect(normalizeStatus(null)).toBe('pending');
		expect(normalizeStatus('totally-unknown')).toBe('pending');
	});
});

describe('statusColor', () => {
	it('uses the ok/fail/teal palette by status', () => {
		expect(statusColor('success')).toBe(color.ok);
		expect(statusColor('failed')).toBe(color.fail);
		expect(statusColor('timedout')).toBe(color.fail);
		expect(statusColor('running')).toBe(color.teal);
	});
});

describe('statusPulses', () => {
	it('pulses only while running', () => {
		expect(statusPulses('running')).toBe(true);
		expect(statusPulses('success')).toBe(false);
		expect(statusPulses('pending')).toBe(false);
	});
});

describe('toolAccent', () => {
	it('returns the per-tool color, falling back for unknown tools', () => {
		expect(toolAccent('gradle')).toBe('#5eead4');
		expect(toolAccent('git')).toBe('#f0a36b');
		expect(toolAccent('nope')).toBe(color.muted2);
	});
});

describe('coverageColor', () => {
	it('maps coverage percentage to a threshold band', () => {
		expect(coverageColor(91)).toBe(color.teal); // healthy ≥80
		expect(coverageColor(80)).toBe(color.teal);
		expect(coverageColor(68)).toBe(color.warn); // warning 60–79
		expect(coverageColor(44)).toBe(color.fail); // poor <60
	});
});
