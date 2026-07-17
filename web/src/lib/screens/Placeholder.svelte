<script lang="ts">
	let { screen }: { screen: string } = $props();
</script>

<div class="ph">
	<div class="diamond"><div class="core"></div></div>
	<div class="k-mono title">{screen}</div>
	<div class="k-mono sub">arriving in a later increment of this feature</div>
</div>

<style>
	.ph {
		height: 100%;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 14px;
	}
	.diamond {
		width: 34px;
		height: 34px;
		border: 1.5px solid var(--k-teal);
		transform: rotate(45deg);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.core {
		width: 10px;
		height: 10px;
		background: var(--k-teal);
		animation: kpulsesoft 3.6s ease-in-out infinite;
	}
	.title {
		font-size: 13px;
		letter-spacing: 3px;
		color: var(--k-heading);
	}
	.sub {
		font-size: 10px;
		letter-spacing: 1px;
		color: var(--k-muted-4);
	}
</style>
