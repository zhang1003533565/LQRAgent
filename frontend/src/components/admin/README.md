# `admin` — 管理端（Dev Console）

路由：`/admin` → `/admin/console`（AI Runtime 开发调试控制台）

| 目录 | 内容 |
|------|------|
| `pages/DevConsolePage.tsx` | 入口（引入 `admin/styles/dev-console.css`） |
| `components/dev-console/` | 布局、Dashboard、日志、业务面板 |
| `components/dev-console/panels/` | 用户 / 上传 / 模型 / 系统配置（Tailwind） |
| `store/` | devConsoleStore |
| `hooks/` | useDevConsoleQueries |
| `types/` | dev-console 专用类型 |
| `constants/` | dev-console-nav |
| `styles/` | dev-console.css |

依赖：`@/shared/*`，禁止 `@/student/*`。
