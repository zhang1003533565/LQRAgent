# `student` — 学生端

路由：`/login`、`/register`、`/workspace/*`。

| 目录 | 内容 |
|------|------|
| `pages/` | LoginPage、WorkspacePage（薄装配） |
| `components/workspace/` | 工作台子模块，见 `components/workspace/README.md` |
| `store/` | chatStore、pathStore、profileStore… |
| `hooks/` | useWebSocket、useChatAutoScroll |

依赖：`@/shared/*`，禁止 `@/admin/*`。
