import { useMemo } from 'react'
import { Button, ConsoleBadge } from '@/components/admin/dev-console/ui'
import { cn } from '@/components/admin/dev-console/lib/utils'
import { useDevConsoleStore } from '@/components/admin/dev-console/store/devConsoleStore'
import type { LogLevel } from '@/components/admin/dev-console/types/dev-console'

const LOG_LEVELS: (LogLevel | 'ALL')[] = ['ALL', 'INFO', 'WARN', 'ERROR', 'DEBUG']

const levelClassName: Record<LogLevel, string> = {
  INFO: 'text-console-green',
  WARN: 'text-console-orange',
  ERROR: 'text-console-red',
  DEBUG: 'text-console-blue',
}

const levelVariant: Record<LogLevel, 'success' | 'warning' | 'danger' | 'default'> = {
  INFO: 'success',
  WARN: 'warning',
  ERROR: 'danger',
  DEBUG: 'default',
}

function formatTime(timestamp: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(timestamp))
}

export function LogPanel() {
  const logs = useDevConsoleStore((s) => s.logs)
  const logFilter = useDevConsoleStore((s) => s.logFilter)
  const setLogFilter = useDevConsoleStore((s) => s.setLogFilter)
  const clearLogs = useDevConsoleStore((s) => s.clearLogs)

  const filteredLogs = useMemo(
    () => (logFilter === 'ALL' ? logs : logs.filter((log) => log.level === logFilter)),
    [logFilter, logs],
  )

  return (
    <section className="flex min-h-0 flex-1 flex-col border-b border-console-border">
      <div className="flex items-center justify-between border-b border-console-border px-3 py-2">
        <div>
          <h2 className="text-sm font-semibold text-console-text">运行日志</h2>
          <p className="text-xs text-console-muted">最近 {filteredLogs.length} 条</p>
        </div>
        <Button variant="ghost" size="sm" onClick={clearLogs}>
          清空
        </Button>
      </div>

      <div className="flex flex-wrap gap-1 border-b border-console-border px-3 py-2">
        {LOG_LEVELS.map((level) => (
          <button
            key={level}
            type="button"
            className={cn(
              'rounded px-2 py-1 text-xs text-console-muted transition-colors hover:bg-console-card hover:text-console-text',
              logFilter === level && 'bg-console-card text-console-text',
            )}
            onClick={() => setLogFilter(level)}
          >
            {level}
          </button>
        ))}
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
        {filteredLogs.length === 0 ? (
          <div className="rounded-lg border border-dashed border-console-border p-4 text-center text-xs text-console-muted">
            暂无日志
          </div>
        ) : (
          <div className="space-y-2">
            {filteredLogs.map((log) => (
              <div key={log.id} className="rounded-lg border border-console-border bg-console-card/60 p-2">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <ConsoleBadge variant={levelVariant[log.level]}>{log.level}</ConsoleBadge>
                  <span className="shrink-0 text-[11px] text-console-muted">{formatTime(log.timestamp)}</span>
                </div>
                <p className={cn('break-words text-xs leading-5', levelClassName[log.level])}>{log.message}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  )
}

export function QuickActions() {
  const appendLog = useDevConsoleStore((s) => s.appendLog)
  const wsConnected = useDevConsoleStore((s) => s.wsConnected)
  const setWsConnected = useDevConsoleStore((s) => s.setWsConnected)

  return (
    <section className="space-y-2 p-3">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-console-text">快捷操作</h2>
        <ConsoleBadge variant={wsConnected ? 'success' : 'danger'}>
          {wsConnected ? 'WS 已连接' : 'WS 断开'}
        </ConsoleBadge>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Button
          variant="secondary"
          size="sm"
          onClick={() =>
            appendLog({
              level: 'INFO',
              message: '手动触发健康检查',
              timestamp: new Date().toISOString(),
              source: 'mock',
            })
          }
        >
          健康检查
        </Button>
        <Button
          variant="secondary"
          size="sm"
          onClick={() => setWsConnected(!wsConnected)}
        >
          切换 WS
        </Button>
      </div>
    </section>
  )
}
