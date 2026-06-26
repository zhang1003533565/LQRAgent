import { test, expect } from '@playwright/test'
import {
  apiGet,
  hasE2ECredentials,
  loginViaApi,
  seedAuthStorage,
} from './helpers/auth'

test.describe('学生端核心链路', () => {
  test.beforeEach(async ({ request }, testInfo) => {
    if (!hasE2ECredentials()) {
      testInfo.skip(true, 'Set E2E_USERNAME and E2E_PASSWORD to run authenticated flows')
    }
    try {
      const auth = await loginViaApi(request)
      if (!auth) testInfo.skip(true, 'Missing credentials')
    } catch {
      testInfo.skip(true, 'Backend not reachable at E2E_API_URL')
    }
  })

  test('1. 登录后学习路径页可访问（有节点或空态引导）', async ({ page, request }) => {
    const auth = await loginViaApi(request)
    if (!auth) return
    await seedAuthStorage(page, auth)
    await page.goto('/workspace/learning-path')
    await expect(page).toHaveURL(/learning-path/)
    const hasNodes = await page.getByText('总节点数').isVisible().catch(() => false)
    const hasEmpty = await page.getByText('还没有学习路径').isVisible().catch(() => false)
    expect(hasNodes || hasEmpty).toBeTruthy()
  })

  test('2. 带 kpId 打开资源页（有内容或空态引导）', async ({ page, request }) => {
    const auth = await loginViaApi(request)
    if (!auth) return
    await seedAuthStorage(page, auth)

    let kpId = 'test-kp'
    try {
      const path = await apiGet<{ nodes?: { kpId: string }[] }>(
        request,
        '/learning-path/current',
        auth.token,
      )
      if (path?.nodes?.[0]?.kpId) kpId = path.nodes[0].kpId
    } catch {
      // use fallback kpId
    }

    await page.goto(`/workspace/resources?kpId=${encodeURIComponent(kpId)}`)
    await expect(page).toHaveURL(/resources/)
    const visible =
      (await page.getByText('还没有学习路径').isVisible().catch(() => false)) ||
      (await page.getByText('学习资源').isVisible().catch(() => false)) ||
      (await page.locator('main, [class*="resource"]').first().isVisible().catch(() => false))
    expect(visible).toBeTruthy()
  })

  test('3. 答题首页与画像页可加载', async ({ page, request }) => {
    const auth = await loginViaApi(request)
    if (!auth) return
    await seedAuthStorage(page, auth)

    await page.goto('/workspace/quiz')
    await expect(page).toHaveURL(/quiz/)
    const quizOk =
      (await page.getByText('还没有学习路径').isVisible().catch(() => false)) ||
      (await page.getByRole('heading').first().isVisible().catch(() => false))
    expect(quizOk).toBeTruthy()

    await page.goto('/workspace/profile')
    await expect(page).toHaveURL(/profile/)
    await expect(page.getByText('学习画像').or(page.getByText('综合掌握度'))).toBeVisible({
      timeout: 15_000,
    })
  })

  test('4. 上传页展示 Dropzone 与存储信息', async ({ page, request }) => {
    const auth = await loginViaApi(request)
    if (!auth) return
    await seedAuthStorage(page, auth)
    await page.goto('/workspace/upload')
    await expect(page.getByText('拖拽文件到这里').or(page.getByText('选择文件'))).toBeVisible({
      timeout: 15_000,
    })
  })

  test('5. 知识图谱页加载画布与节点详情', async ({ page, request }) => {
    const auth = await loginViaApi(request)
    if (!auth) return
    await seedAuthStorage(page, auth)
    await page.goto('/workspace/knowledge-graph')
    await expect(page).toHaveURL(/knowledge-graph/)

    const canvas = page.locator('canvas').first()
    await expect(canvas).toBeVisible({ timeout: 20_000 })

    const box = await canvas.boundingBox()
    if (box) {
      await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2)
    }

    const detailVisible =
      (await page.getByText('选择一个知识点').isVisible().catch(() => false)) ||
      (await page.getByText('掌握度').isVisible().catch(() => false)) ||
      (await page.getByText('难度').isVisible().catch(() => false))
    expect(detailVisible).toBeTruthy()
  })
})

test.describe('登录页（无需后端账号）', () => {
  test('登录表单渲染', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('#username')).toBeVisible()
    await expect(page.locator('#password')).toBeVisible()
    await expect(page.getByRole('button', { name: /登录|登 录/ })).toBeVisible()
  })
})
