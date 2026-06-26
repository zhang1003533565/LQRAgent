import { defineConfig, devices } from '@playwright/test'

/**
 * 学生端 E2E — 需同时启动 backend:8080 与 frontend dev/preview。
 *
 * 环境变量：
 * - E2E_BASE_URL   默认 http://localhost:5173
 * - E2E_API_URL    默认 http://localhost:8080/api
 * - E2E_USERNAME / E2E_PASSWORD  测试账号（未设置则跳过需登录用例）
 */
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: process.env.E2E_SKIP_WEB_SERVER
    ? undefined
    : {
        command: 'npm run dev',
        url: BASE_URL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
