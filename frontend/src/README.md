# `src` 目录说明

按 **student / admin / shared** 三大域划分，业务互不 import，公共能力走 `shared`。

```
src/
├── app/                    # 应用壳：路由
├── shared/                 # 鉴权、HTTP、协议类型、学生端通用 UI
│   ├── api/
│   ├── store/authStore.ts
│   ├── types/
│   ├── constants/
│   ├── components/ui/
│   ├── components/auth/
│   └── styles/workspace-theme.css
├── student/                # 学生端（/workspace）
│   ├── pages/
│   ├── components/workspace/
│   ├── store/              # chat、path、profile…
│   └── hooks/              # useWebSocket、useChatAutoScroll
├── admin/                  # 管理端 Dev Console（/admin/console）
│   ├── pages/
│   ├── components/dev-console/
│   ├── store/              # devConsoleStore
│   ├── hooks/              # useDevConsoleQueries
│   ├── types/dev-console.ts
│   ├── constants/dev-console-nav.ts
│   └── styles/dev-console.css
├── index.css
└── main.tsx
```

**导入约定**

| 用途 | 路径前缀 |
|------|----------|
| 公共 | `@/shared/...` |
| 学生端 | `@/student/...` |
| 管理端 | `@/admin/...` |
| 路由壳 | `@/app/...` |

**禁止**：`student` ↔ `admin` 互相引用业务组件；跨域只通过 `shared`。
