<script lang="ts">
	export interface NavItem {
		label: string;
		href: string;
	}

	let {
		active = '/',
		lanes = 3,
		operator = 'operator',
		onexit
	}: { active?: string; lanes?: number; operator?: string; onexit?: () => void } = $props();

	const items: NavItem[] = [
		{ label: 'RUNS', href: '/' },
		{ label: 'PIPELINE', href: '/pipeline' },
		{ label: 'DEPLOY', href: '/deploy' },
		{ label: 'COVERAGE', href: '/coverage' },
		{ label: 'CONFIG', href: '/config' }
	];

	function isActive(href: string): boolean {
		if (href === '/') return active === '/' || active.startsWith('/runs');
		return active.startsWith(href);
	}

	const initials = $derived(operator.slice(0, 2).toUpperCase());
</script>

<aside class="sidebar">
	<div class="brand">
		<div class="mark"><div class="core"></div></div>
		<div>
			<div class="title">KONTINUANCE</div>
			<div class="sub k-mono">CI // ORBITAL v0.4</div>
		</div>
	</div>

	<nav>
		{#each items as item (item.href)}
			<a class="nav" class:active={isActive(item.href)} href={item.href}>
				<span class="bar"></span>
				<span class="k-mono label">{item.label}</span>
			</a>
		{/each}
	</nav>

	<div class="foot">
		<div class="status">
			<span class="ok-dot"></span>
			<span class="k-mono">ALL SYSTEMS NOMINAL</span>
		</div>
		<div class="k-mono meta">agents 4/4 · lanes {lanes}</div>
		<div class="user">
			<div class="avatar k-mono">{initials}</div>
			<span class="k-mono uname">{operator}</span>
			<button class="k-mono exit" onclick={() => onexit?.()}>EXIT</button>
		</div>
	</div>
</aside>

<style>
	.sidebar {
		width: 208px;
		flex: none;
		display: flex;
		flex-direction: column;
		border-right: 1px solid var(--k-border-soft);
		background: rgba(10, 15, 21, 0.85);
	}
	.brand {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 22px 20px 18px;
		border-bottom: 1px solid var(--k-border-soft);
	}
	.mark {
		width: 26px;
		height: 26px;
		flex: none;
		border: 1.5px solid var(--k-teal);
		transform: rotate(45deg);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.core {
		width: 8px;
		height: 8px;
		background: var(--k-teal);
		animation: kpulsesoft 3.6s ease-in-out infinite;
	}
	.title {
		font-weight: 700;
		font-size: 14px;
		letter-spacing: 2.5px;
		color: var(--k-heading);
	}
	.sub {
		font-size: 9px;
		letter-spacing: 1.5px;
		color: var(--k-muted-4);
		margin-top: 2px;
	}
	nav {
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: 14px 10px;
	}
	.nav {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 12px;
		border-radius: 4px;
		color: inherit;
	}
	.nav:hover {
		background: rgba(94, 234, 212, 0.06);
	}
	.nav.active {
		background: rgba(94, 234, 212, 0.06);
	}
	.bar {
		width: 3px;
		height: 16px;
		border-radius: 2px;
		background: var(--k-faint-2);
	}
	.nav.active .bar {
		background: var(--k-teal);
	}
	.label {
		font-size: 11px;
		letter-spacing: 2px;
		color: var(--k-muted-4);
	}
	.nav.active .label {
		color: var(--k-teal);
	}
	.foot {
		margin-top: auto;
		padding: 16px 20px;
		border-top: 1px solid var(--k-border-soft);
	}
	.status {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 9.5px;
		letter-spacing: 1.5px;
		color: var(--k-muted-4);
	}
	.ok-dot {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--k-ok);
		animation: kpulsesoft 3.6s ease-in-out infinite;
	}
	.meta {
		font-size: 9.5px;
		color: var(--k-faint);
		margin-top: 8px;
	}
	.user {
		display: flex;
		align-items: center;
		gap: 8px;
		margin-top: 12px;
		padding-top: 12px;
		border-top: 1px solid #101a23;
	}
	.avatar {
		width: 20px;
		height: 20px;
		flex: none;
		border-radius: 50%;
		background: rgba(94, 234, 212, 0.12);
		border: 1px solid rgba(94, 234, 212, 0.3);
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 9px;
		color: var(--k-teal);
	}
	.uname {
		font-size: 9.5px;
		color: var(--k-muted-3);
	}
	.exit {
		margin-left: auto;
		font-size: 8.5px;
		letter-spacing: 1px;
		color: var(--k-faint);
		background: none;
		border: none;
		cursor: pointer;
		padding: 0;
	}
	.exit:hover {
		color: var(--k-fail);
	}
</style>
