import type { Pipeline } from '$lib/api/types';

export const samplePipeline: Pipeline = {
	runId: '#KX-2046',
	stages: [
		{ id: 's1', name: 'CHECKOUT', tasks: [{ id: 'git', name: 'git checkout', tool: 'git', status: 'success', progress: 100, deps: [] }] },
		{
			id: 's2',
			name: 'SETUP ENV',
			tasks: [
				{ id: 'jdk', name: 'provision jdk 21', tool: 'env', status: 'success', progress: 100, deps: [] },
				{ id: 'cache', name: 'restore gradle cache', tool: 'cache', status: 'success', progress: 100, deps: [] }
			]
		},
		{
			id: 's3',
			name: 'BUILD',
			tasks: [
				{ id: 'core', name: ':core assemble', tool: 'gradle', status: 'success', progress: 100, deps: [] },
				{ id: 'api', name: ':api assemble', tool: 'gradle', status: 'success', progress: 100, deps: [] },
				{ id: 'legacy', name: 'legacy-adapter package', tool: 'maven', status: 'running', progress: 62, deps: ['jdk'] }
			]
		},
		{
			id: 's4',
			name: 'TEST',
			tasks: [
				{ id: 'unit', name: 'unit tests', tool: 'gradle', status: 'running', progress: 40, deps: ['core', 'api'] },
				{ id: 'integ', name: 'integration tests', tool: 'gradle', status: 'pending', progress: 0, deps: [] },
				{ id: 'lint', name: 'static analysis', tool: 'lint', status: 'pending', progress: 0, deps: ['git'] }
			]
		},
		{ id: 's5', name: 'PUBLISH', tasks: [{ id: 'pub', name: 'publish → repo manager', tool: 'nexus', status: 'pending', progress: 0, deps: [] }] },
		{ id: 's6', name: 'DEPLOY', tasks: [{ id: 'argo', name: 'argocd sync → stage', tool: 'argo', status: 'pending', progress: 0, deps: ['pub'] }] }
	]
};
