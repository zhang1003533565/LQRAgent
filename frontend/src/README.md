# `src` 目录说明

按常见 React + TypeScript 项目约定组织（feature-first + 共享层分离）。

```
src/
├── app/                 # 应用壳：路由、鉴权守卫
├── pages/               # 路由页面（薄层，只做装配）
├── features/            # 业务功能域
│   ├── workspace/       # 学生工作台
│   └── admin/           # 管理后台面板
├── components/
│   └── ui/              # 无业务逻辑的通用 UI（每组件一目录 + index）
├── api/                 # HTTP 客户端
├── store/               # Zustand 全局状态
├── hooks/               # 可复用 Hooks
├── types/               # 共享类型
├── constants/           # 常量
├── styles/              # 全局样式与主题变量
├── assets/              # 静态资源
└── main.tsx             # 入口
```

**导入约定**

- 业务功能：`@/features/<domain>`
- 通用 UI：`@/components/ui`
- 禁止从 `features` 互相深层穿透，跨域共享放 `components` / `hooks` / `types`
