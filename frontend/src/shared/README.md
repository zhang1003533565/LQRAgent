# `shared` — 公共层

学生端与管理端共用，不含业务页面。

| 目录 | 内容 |
|------|------|
| `api/` | axios 封装、`auth`、`admin`、`upload` 等 |
| `store/` | `authStore`（登录态持久化） |
| `types/` | WS 协议、聊天、路径、画像等类型 |
| `constants/` | `agent-labels` 等 |
| `components/ui/` | Badge、EmptyState（CSS Modules） |
| `components/auth/` | `ProtectedRoute` |
| `styles/` | `workspace-theme.css`（学生工作台主题变量） |
