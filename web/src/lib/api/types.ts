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

// A run's recorded output (018): already-masked, step-prefixed lines in production order.
export interface RunLogs {
	runId: string;
	lines: string[];
}

// The UI's read of the current auth state (016 server). `authRequired` is false in open mode;
// `username` is present only when authenticated. Mirrors /api/auth/me and /api/auth/login.
export interface Session {
	authenticated: boolean;
	authRequired: boolean;
	username?: string;
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

// A class-level coverage row within a module (031 drilldown).
export interface CoverageClass {
	name: string;
	linePct: number;
	branchPct: number;
	missed: number;
}

export interface CoverageModule {
	name: string;
	kind: string;
	linePct: number;
	branchPct: number;
	missed: number;
	// Per-class breakdown, worst-covered first; absent for old/fixture responses.
	classes?: CoverageClass[];
}

export interface Coverage {
	tool: 'kover';
	line: CoverageMetric;
	branch: CoverageMetric;
	classes: number;
	modules: CoverageModule[];
}

// A named pipeline descriptor the server stores and can run (032). `active` marks the one in effect.
// `repo`/`branch` are the project's optional source (033): when set, a run of the project checks them out.
export interface Project {
	name: string;
	active: boolean;
	repo?: string;
	branch?: string;
}
export interface ProjectsResponse {
	active: string | null;
	projects: Project[];
}

// The GitHub event source (035), read-only. When `configured` is false the rest is absent.
export interface SourceRepo {
	slug: string;
	prPipeline: string;
	pushPipeline?: string;
	trackedBranch: string;
}
export interface SourceCursor {
	key: string;
	sha: string;
}
// Liveness of the poller (036): present once it has completed a poll; absent means "unknown".
export interface SourceHeartbeat {
	lastPolledMillis: number;
	ageSeconds: number;
	stale: boolean;
	cycles: number;
}
export interface SourceStatus {
	configured: boolean;
	pollIntervalSeconds?: number;
	baseUrl?: string;
	tokenEnv?: string;
	repositories?: SourceRepo[];
	cursors?: SourceCursor[];
	heartbeat?: SourceHeartbeat;
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
