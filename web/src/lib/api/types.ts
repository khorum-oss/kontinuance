// Types mirroring the server API. The run shape is the real 007/008 record; the pipeline/deploy/coverage/
// config shapes mirror specs/009-web-ui/contracts/stub-api.md (served by the stub endpoints for now).

export interface RunRecord {
	id: string;
	pipeline: string;
	status: string;
	failingStep?: string;
	reason?: string;
	startedAt?: string;
	endedAt?: string;
	repo?: string;
	sha?: string;
	trigger?: string;
}

export interface RunsResponse {
	runs: RunRecord[];
}

// ----- stub contracts (forward-looking screens) -----

export type TaskTool =
	| 'git'
	| 'gradle'
	| 'maven'
	| 'env'
	| 'cache'
	| 'lint'
	| 'bun'
	| 'oci'
	| 'nexus'
	| 'argo';

export type TaskStatus = 'pending' | 'running' | 'success' | 'failed' | 'skipped';

export interface PipelineTask {
	id: string;
	name: string;
	tool: TaskTool;
	status: TaskStatus;
	progress: number; // 0..100
	deps: string[];
}

export interface PipelineStage {
	id: string;
	name: string;
	tasks: PipelineTask[];
}

export interface Pipeline {
	runId: string;
	stages: PipelineStage[];
}

export interface DeployNode {
	id: string;
	label: string;
	title: string;
	status: string;
	meta: string;
}

export interface Artifact {
	kind: string;
	name: string;
	digest: string;
	state: string;
}

export interface DeployEnvironment {
	podsReady: string;
	syncRevision: string;
	health: string;
	meta: string;
}

export interface Deploy {
	nodes: DeployNode[];
	artifacts: Artifact[];
	environment: DeployEnvironment;
}

export interface CoverageMetric {
	pct: string;
	covered: number;
	total: number;
}

export interface CoverageModule {
	name: string;
	kind: string;
	linePct: number;
	branchPct: number;
	missed: number;
}

export interface Coverage {
	tool: 'kover';
	line: CoverageMetric;
	branch: CoverageMetric;
	classes: number;
	modules: CoverageModule[];
}

export interface PlanSummary {
	stages: number;
	tasks: number;
	maxParallel: number;
	toolchain: string;
	publish: string;
	deploy: string;
}

export interface Config {
	source: string;
	text: string;
	plan: PlanSummary;
}
