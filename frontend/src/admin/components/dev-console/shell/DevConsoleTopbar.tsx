import { RefreshCw, Settings } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import {
  Avatar,
  AvatarFallback,
  Button,
  ConsoleBadge,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/admin/components/dev-console/ui'
import { useDevConsoleOverview } from '@/admin/components/dev-console/hooks/useDevConsoleQueries'
import { useDevConsoleStore } from '@/admin/components/dev-console/store/devConsoleStore'
import { useAuthStore } from '@/student/store/authStore'
import { formatTokens, MOCK_METRICS } from '@/admin/components/dev-console/mock/data'

export default function DevConsoleTopbar() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const wsConnected = useDevConsoleStore((s) => s.wsConnected)
  const { adminStatus, refetch } = useDevConsoleOverview()
  const qc = useQueryClient()

  async function handleRefresh() {
    await refetch()
    await qc.invalidateQueries({ queryKey: ['dev-console'] })
  }

  const aiOk = adminStatus?.aiServerReachable ?? true

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-console-border bg-console-card/60 px-4 backdrop-blur-xl">
      <div className="flex items-center gap-3">
        <span className="text-lg font-semibold tracking-tight text-console-text">LQRAgent</span>
        <span className="text-sm text-console-muted">Dev Console</span>
      </div>

      <div className="hidden items-center gap-2 md:flex">
        <ConsoleBadge variant={aiOk ? 'success' : 'danger'}>
          <span className="mr-1.5 inline-block h-1.5 w-1.5 rounded-full bg-current" />
          AI Server {aiOk ? '在线' : '不可达'}
        </ConsoleBadge>
        <ConsoleBadge variant={wsConnected ? 'success' : 'warning'}>
          WebSocket {wsConnected ? '已连接' : '断开'}
        </ConsoleBadge>
        <ConsoleBadge variant="success">MySQL 已连接</ConsoleBadge>
      </div>

      <div className="flex items-center gap-4">
        <div className="hidden text-right text-xs lg:block">
          <p className="text-console-muted">
            今日 Token{' '}
            <span className="font-mono text-console-text">
              {formatTokens(MOCK_METRICS.tokensToday)}
            </span>
            <span className="ml-2 text-[10px] text-console-orange">MOCK</span>
          </p>
          <p className="text-console-muted">
            在线用户{' '}
            <span className="font-mono text-console-cyan">{MOCK_METRICS.onlineUsers}</span>
          </p>
        </div>
        <Button variant="ghost" size="icon" onClick={() => void handleRefresh()} title="刷新">
          <RefreshCw className="h-4 w-4" />
        </Button>
        <Button variant="ghost" size="icon" title="设置">
          <Settings className="h-4 w-4" />
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button type="button" className="rounded-full outline-none ring-console-blue focus-visible:ring-2">
              <Avatar>
                <AvatarFallback>{user?.username?.slice(0, 2).toUpperCase() ?? 'AD'}</AvatarFallback>
              </Avatar>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel>{user?.username ?? 'admin'}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={logout}>退出登录</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
