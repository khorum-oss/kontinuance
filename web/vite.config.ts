/// <reference types="vitest/config" />
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

// The Kontinuance server (read API + live stream). Override with KONTINUANCE_API when developing
// against a remote instance. The dev server proxies /api and /ws to it so the SPA shares an origin.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { storybookTest } from '@storybook/addon-vitest/vitest-plugin';
import { playwright } from '@vitest/browser-playwright';
const dirname = typeof __dirname !== 'undefined' ? __dirname : path.dirname(fileURLToPath(import.meta.url));

// More info at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon
const API = process.env.KONTINUANCE_API ?? 'http://localhost:8077';
export default defineConfig({
  plugins: [sveltekit({
    compilerOptions: {
      // Force runes mode for the project, except for libraries. Can be removed in svelte 6.
      runes: ({
        filename
      }) => filename.split(/[/\\]/).includes('node_modules') ? undefined : true
    },
    // Static adapter: the UI is a client-rendered SPA that talks to the server over HTTP + the
    // live stream, so it ships as static assets with an index.html fallback for client routing.
    adapter: adapter({
      fallback: 'index.html'
    })
  })],
  server: {
    proxy: {
      '/api': {
        target: API,
        changeOrigin: true
      },
      '/ws': {
        target: API,
        ws: true,
        changeOrigin: true
      }
    }
  },
  test: {
    projects: [{
      extends: true,
      plugins: [
      // The plugin will run tests for the stories defined in your Storybook config
      // See options at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon#storybooktest
      storybookTest({
        configDir: path.join(dirname, '.storybook')
      })],
      test: {
        name: 'storybook',
        browser: {
          enabled: true,
          headless: true,
          provider: playwright({}),
          instances: [{
            browser: 'chromium'
          }]
        }
      }
    }]
  }
});