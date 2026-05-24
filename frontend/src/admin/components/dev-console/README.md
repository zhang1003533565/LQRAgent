# Dev Console（`admin/components/dev-console`）

AI 运行时开发调试控制台。入口：`admin/pages/DevConsolePage.tsx` → `/admin/console`。

| 目录 | 职责 |
|------|------|
| `shell/` | Topbar + Sidebar + 主区布局 |
| `dashboard/` | 概览指标、服务状态、Agent、Trace |
| `logs/` | 右侧日志流 + 快捷操作 |
| `placeholder/` | 未实现子页占位 |
| `ui/` | Console 专用 shadcn（Tailwind） |
| `mock/` | MOCK 数据 |

**本域资源（勿放 `shared`）**

- `@/admin/store/devConsoleStore`
- `@/admin/hooks/useDevConsoleQueries`
- `@/admin/types/dev-console`
- `@/admin/constants/dev-console-nav`
- `@/admin/styles/dev-console.css`

**跨域共用**

- `@/shared/api/admin`（`getAdminStatus`）
- `@/shared/types/agent-events`、`@/shared/constants/agent-labels`
