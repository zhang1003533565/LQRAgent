import { motion } from 'framer-motion'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { DEV_CONSOLE_NAV_LABEL } from '@/components/admin/dev-console/constants/dev-console-nav'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

interface DevConsolePlaceholderProps {
  navId: DevConsoleNavId
}

/** 未实现子页占位 */
export default function DevConsolePlaceholder({ navId }: DevConsolePlaceholderProps) {
  const title = DEV_CONSOLE_NAV_LABEL[navId]
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
      <Card>
        <CardHeader>
          <CardTitle>{title}</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-console-muted">
          <p>模块规划中，与 LQRAgent 后端能力对齐后实现。</p>
          <ul className="mt-3 list-inside list-disc space-y-1">
            {navId === 'agent-debug' && (
              <li>主区已提供 Agent 状态；完整调试见侧栏「概览」</li>
            )}
            {navId === 'logs' && (
              <li>实时日志见右侧 Log Panel；后续可扩展全屏日志视图</li>
            )}
            {navId === 'profile' && <li>对齐 GET /api/profile/summary、profile_patch WS</li>}
            {navId === 'knowledge' && <li>对齐课程 seed_kb、RAG 检索</li>}
            {navId === 'path' && <li>对齐 GET /api/learning-path/current</li>}
            {navId === 'resources' && <li>对齐资源生成 artifact（P1）</li>}
            {navId === 'prompts' && <li>Prompt 模板 CRUD（规划中）</li>}
            {navId === 'params' && <li>运行时参数调优（规划中）</li>}
          </ul>
        </CardContent>
      </Card>
    </motion.div>
  )
}
