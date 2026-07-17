// Typed example data for stories and (later) stub screens. Not used by the wired screens, which read
// only the real API.

import type { RunView } from '$lib/api/present';
import type { RunRecord } from '$lib/api/types';

export const sampleRunViews: RunView[] = [
	{
		id: '#KX-2046',
		status: 'running',
		ref: 'khorum-oss/kontinuance · a3f19c2',
		message: 'fix: gradle daemon flags',
		progress: 66,
		indeterminate: true,
		duration: '—',
		age: '26s'
	},
	{
		id: '#KX-2045',
		status: 'success',
		ref: 'khorum-oss/kontinuance · 9b02d1e',
		message: 'argo application manifest',
		progress: 100,
		indeterminate: false,
		duration: '5m 01s',
		age: '1h'
	},
	{
		id: '#KX-2044',
		status: 'failed',
		ref: 'khorum-oss/kontinuance · 77aa310',
		message: 'integration tests: 2 failed',
		progress: 100,
		indeterminate: false,
		duration: '2m 47s',
		age: '3h'
	},
	{
		id: '#KX-2043',
		status: 'success',
		ref: 'khorum-oss/kontinuance · c19ee04',
		message: 'wip: dsl tokenizer',
		progress: 100,
		indeterminate: false,
		duration: '4m 55s',
		age: '5h'
	},
	{
		id: '#KX-2042',
		status: 'cancelled',
		ref: 'khorum-oss/kontinuance · 5d4c8b7',
		message: 'chore: bump jdk 21.0.3',
		progress: 100,
		indeterminate: false,
		duration: '3m 58s',
		age: '8h'
	}
];

/** Raw records in the server's wire shape (for exercising the presentation helpers). */
export const sampleRunRecords: RunRecord[] = [
	{
		id: '#KX-2045',
		pipeline: 'kontinuance-service',
		status: 'Success',
		startedAt: '2026-07-17T00:55:00Z',
		endedAt: '2026-07-17T01:00:01Z',
		repo: 'khorum-oss/kontinuance',
		sha: '9b02d1e0a',
		trigger: 'push'
	},
	{
		id: '#KX-2044',
		pipeline: 'kontinuance-service',
		status: 'Failed',
		failingStep: 'integration tests',
		reason: '2 failed',
		startedAt: '2026-07-17T00:40:00Z',
		endedAt: '2026-07-17T00:42:47Z',
		repo: 'khorum-oss/kontinuance',
		sha: '77aa3101f',
		trigger: 'pull_request'
	}
];
