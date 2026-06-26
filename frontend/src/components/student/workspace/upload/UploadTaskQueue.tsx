import { RotateCcw, X } from 'lucide-react'
import { formatBytes } from '@/utils/upload/uploadConstants'
import type { ClientUploadTask, UploadConfig } from '@/utils/types/upload'

const STATUS_LABEL: Record<ClientUploadTask['status'], string> = {
  queued: '等待上传',
  uploading: '上传中',
  success: '上传完成',
  failed: '上传失败',
  canceled: '已取消',
}

function FileIcon({ name }: { name: string }) {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  const color =
    ext === 'pdf'
      ? 'bg-[#FEE2E2] text-[#EF4444]'
      : ['doc', 'docx'].includes(ext)
        ? 'bg-[#DBEAFE] text-[#2563EB]'
        : ['ppt', 'pptx'].includes(ext)
          ? 'bg-[#FFEDD5] text-[#F59E0B]'
          : 'bg-[#EAF3FF] text-[#2563EB]'
  return (
    <div className={`flex h-10 w-10 items-center justify-center rounded-xl text-xs font-bold ${color}`}>
      {ext.slice(0, 3).toUpperCase() || 'FILE'}
    </div>
  )
}

type ItemProps = {
  task: ClientUploadTask
  onCancel: () => void
  onRetry: () => void
}

export function UploadTaskItem({ task, onCancel, onRetry }: ItemProps) {
  return (
    <div className="flex min-h-[64px] items-center gap-3 rounded-xl px-3 py-2 hover:bg-[#F8FBFF]">
      <FileIcon name={task.fileName} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-sm font-semibold text-[#334155]">{task.fileName}</p>
          <span className="text-xs text-[#64748B]">{formatBytes(task.fileSizeBytes)}</span>
        </div>
        {(task.status === 'uploading' || task.status === 'queued') && (
          <div className="mt-2 h-2 rounded-full bg-[#E8EEF7]">
            <div
              className="h-full rounded-full bg-[#2563EB] transition-all"
              style={{ width: `${task.progress}%` }}
            />
          </div>
        )}
        {task.errorMessage ? (
          <p className="mt-1 text-xs text-[#EF4444]">{task.errorMessage}</p>
        ) : null}
      </div>
      <div className="flex items-center gap-2">
        <span className="text-xs font-medium text-[#64748B]">{STATUS_LABEL[task.status]}</span>
        {task.status === 'failed' ? (
          <button type="button" onClick={onRetry} className="rounded-lg p-1.5 text-[#2563EB] hover:bg-[#EAF3FF]">
            <RotateCcw className="h-4 w-4" />
          </button>
        ) : null}
        {task.status === 'uploading' || task.status === 'queued' ? (
          <button type="button" onClick={onCancel} className="rounded-lg p-1.5 text-[#64748B] hover:bg-[#F1F5F9]">
            <X className="h-4 w-4" />
          </button>
        ) : null}
      </div>
    </div>
  )
}

type Props = {
  tasks: ClientUploadTask[]
  config: UploadConfig
  onCancel: (clientId: string) => void
  onRetry: (clientId: string) => void
  onClearCompleted: () => void
}

export default function UploadTaskQueue({ tasks, config, onCancel, onRetry, onClearCompleted }: Props) {
  const active = tasks.filter((t) => t.status === 'queued' || t.status === 'uploading')
  if (tasks.length === 0) return null

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-base font-bold text-[#0F2A5F]">上传队列</h2>
        <div className="flex items-center gap-3 text-xs text-[#64748B]">
          {active.length > 0 ? <span>正在上传 {active.length} 个</span> : null}
          <button type="button" onClick={onClearCompleted} className="text-[#2563EB]">
            清除已完成
          </button>
        </div>
      </div>
      <div className="space-y-1">
        {tasks.map((task) => (
          <UploadTaskItem
            key={task.clientId}
            task={task}
            onCancel={() => onCancel(task.clientId)}
            onRetry={() => onRetry(task.clientId)}
          />
        ))}
      </div>
    </section>
  )
}
