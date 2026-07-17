import { defineConfig, devices } from '@playwright/test';

// E2E runs against the dev server; the specs intercept /api/* at the browser layer (see e2e/mock.ts),
// so no real backend is needed. In the sandbox a pre-installed Chromium is used via PW_CHROMIUM_PATH.
const executablePath = process.env.PW_CHROMIUM_PATH || undefined;

export default defineConfig({
	testDir: './e2e',
	timeout: 30_000,
	expect: { timeout: 8_000 },
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	retries: process.env.CI ? 1 : 0,
	reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
	use: {
		baseURL: 'http://localhost:4173',
		trace: 'on-first-retry'
	},
	projects: [
		{ name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: { executablePath } } }
	],
	webServer: {
		command: 'pnpm dev --port 4173 --strictPort',
		url: 'http://localhost:4173',
		reuseExistingServer: !process.env.CI,
		timeout: 120_000
	}
});
