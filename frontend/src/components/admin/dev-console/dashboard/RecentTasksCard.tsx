import { motion } from 'framer-motion'
import { Card, CardContent, CardHeader, CardTitle, ConsoleBadge } from '@/components/admin/dev-console/ui'
import type { RecentTask } from '@/components/admin/dev-console/types/dev-console'

interface RecentTasksCardProps {
  tasks: RecentTask[]
}

export default function RecentTasksCard({ tasks }: RecentTasksCardProps) {
  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>最近任务</CardTitle>
        <p className="text-xs text-console-muted">
          MOCK · 后续对齐上传队列 / 编排任务 API
        </p>
      </CardHeader>
      <CardContent className="space-y-2">
        {tasks.map((task, i) => (
          <motion.div
            key={task.id}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06 }}
            className="flex items-center justify-between gap-2 rounded-md border border-console-border/60 px-3 py-2.5"
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{task.studentId}</p>
              <p className="truncate text-xs text-console-muted">{task.description}</p>
            </div>
            <ConsoleBadge
              variant={
                task.status === 'running'
                  ? 'success'
                  : task.status === 'failed'
                    ? 'danger'
                    : 'muted'
              }
            >
              {task.status === 'running' ? 'Running' : task.status === 'failed' ? 'Failed' : 'Done'}
            </ConsoleBadge>
          </motion.div>
        ))}
      </CardContent>
    </Card>
  )
}
