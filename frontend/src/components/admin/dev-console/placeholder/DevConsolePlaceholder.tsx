import { Card, CardContent, CardHeader, CardTitle } from '@/components/admin/dev-console/ui'
import { DEV_CONSOLE_NAV_LABEL } from '@/components/admin/dev-console/constants/dev-console-nav'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

interface DevConsolePlaceholderProps {
  navId: DevConsoleNavId
}

/** 未实现子页占位（当前所有导航项已实现，此组件作为兜底） */
export default function DevConsolePlaceholder({ navId }: DevConsolePlaceholderProps) {
  const title = DEV_CONSOLE_NAV_LABEL[navId] ?? navId
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="text-sm text-console-muted">
        <p>模块开发中，敬请期待。</p>
      </CardContent>
    </Card>
  )
}
