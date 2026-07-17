import type { Config, Deploy } from '$lib/api/types';

export const sampleDeploy: Deploy = {
	nodes: [
		{ id: 'build', label: 'SOURCE', title: 'kontinuance-service', status: 'synced', meta: 'commit a3f19c2\nbuilt 1.4.2' },
		{ id: 'stage', label: 'STAGE', title: 'argocd / kontinuance-stage', status: 'progressing', meta: 'sync 1.4.2 → live\nrollout 2/3' },
		{ id: 'prod', label: 'PROD', title: 'manual promotion gate', status: 'pending', meta: 'promotes by digest\nawaiting approval' }
	],
	artifacts: [
		{ kind: 'JAR', name: 'kontinuance-core-1.4.2.jar', digest: 'sha256:8c1e42aa', state: 'published' },
		{ kind: 'JAR', name: 'kontinuance-api-1.4.2.jar', digest: 'sha256:5b90d17c', state: 'published' },
		{ kind: 'OCI', name: 'kontinuance:1.4.2', digest: 'sha256:8c1e42aa', state: 'pushed' }
	],
	environment: {
		podsReady: '2/3',
		syncRevision: '1.4.2',
		health: 'Progressing',
		meta: 'namespace kontinuance-stage\nargocd auto-sync on'
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
