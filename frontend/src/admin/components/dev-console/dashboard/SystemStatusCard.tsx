import { motion } from 'framer-motion'
import { Card, CardContent, CardHeader, CardTitle } from '@/admin/components/dev-console/ui'
import type { ServiceHealth } from '@/admin/types/dev-console'

interface SystemStatusCardProps {
  services: ServiceHealth[]
  uptime?: string
}

const STATUS_LABEL: Record<string, string> = {
  online: 'ONLINE',
  connected: 'CONNECTED',
  degraded: 'DEGRADED',
  offline: 'OFFLINE',
}

function StatusDot({ status }: { status: ServiceHealth['status'] }) {
  const ok = status === 'online' || status === 'connected'
  return (
    <span
      className={`inline-block h-2 w-2 rounded-full ${ok ? 'bg-console-green animate-pulseDot' : 'bg-console-orange'}`}
    />
  )
}

export default function SystemStatusCard({ services, uptime = '2 天 6 小时' }: SystemStatusCardProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>系统运行状态</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {services.map((svc, i) => (
          <motion.div
            key={svc.id}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: i * 0.04 }}
            className="flex items-center justify-between rounded-md border border-console-border/60 bg-console-bg/40 px-3 py-2"
          >
            <div className="flex items-center gap-2">
              <StatusDot status={svc.status} />
              <span className="text-sm">{svc.name}</span>
              {svc.source === 'mock' && (
                <span className="text-[10px] text-console-muted">MOCK</span>
              )}
            </div>
            <span className="font-mono text-xs text-console-green">
              {STATUS_LABEL[svc.status] ?? svc.status.toUpperCase()}
            </span>
          </motion.div>
        ))}
        <div className="mt-4 border-t border-console-border pt-3 text-xs text-console-muted">
          <p>启动时间：2026-05-22 08:00</p>
          <p className="mt-1">运行时长：{uptime}</p>
        </div>
      </CardContent>
    </Card>
  )
}
