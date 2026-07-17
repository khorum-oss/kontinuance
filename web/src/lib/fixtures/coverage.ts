import type { Coverage } from '$lib/api/types';

export const sampleCoverage: Coverage = {
	tool: 'kover',
	line: { pct: '84.2%', covered: 4821, total: 5724 },
	branch: { pct: '72.1%', covered: 611, total: 848 },
	classes: 142,
	modules: [
		{ name: 'engine', kind: 'module', linePct: 91, branchPct: 84, missed: 214 },
		{ name: 'persistence', kind: 'module', linePct: 88, branchPct: 79, missed: 46 },
		{ name: 'github', kind: 'module', linePct: 83, branchPct: 71, missed: 118 },
		{ name: 'server', kind: 'module', linePct: 86, branchPct: 74, missed: 63 },
		{ name: 'dsl', kind: 'module', linePct: 78, branchPct: 66, missed: 90 }
	]
};
