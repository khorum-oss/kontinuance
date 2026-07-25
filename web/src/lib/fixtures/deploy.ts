import type { Config, Deploy } from '$lib/api/types';

// Run-derived delivery view (022): a SOURCE node + a node per the run's real stages; no artifact
// registry (external), and the environment is the run's real stage-completion / commit / state.
export const sampleDeploy: Deploy = {
	nodes: [
		{ id: 'source', label: 'SOURCE', title: 'khorum-oss/kontinuance', status: 'synced', meta: 'commit a3f19c2\ntrigger manual' },
		{ id: 'stage-0', label: 'BUILD', title: '1 step', status: 'synced', meta: 'assemble — synced' },
		{ id: 'stage-1', label: 'TEST', title: '1 step', status: 'failed', meta: 'unit — failed' }
	],
	artifacts: [],
	environment: {
		podsReady: '1/2',
		syncRevision: 'a3f19c2',
		health: 'FAILED',
		meta: 'Registry & ArgoCD sync are external — not tracked by Kontinuance. This view derives from the latest run.'
	}
};

export const sampleConfig: Config = {
	source: 'kontinuance.yml',
	text: [
		'# kontinuance.yml — pipeline definition',
		'version: 0.4',
		'project: kontinuance-service',
		'toolchain:',
		'  jdk: 21',
		'  gradle: 8.8',
		'stages:',
		'  - checkout',
		'  - build',
		'  - test',
		'  - publish',
		'  - deploy'
	].join('\n'),
	plan: {
		stages: 6,
		tasks: 10,
		maxParallel: 3,
		toolchain: 'temurin-21 · gradle 8.8',
		publish: 'nexus.internal',
		deploy: 'argocd / kontinuance-stage'
	}
};
