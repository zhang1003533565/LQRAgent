import {
  CheckCircle2,
  Circle,
  FileSearch,
  GitBranch,
  Loader2,
  Sparkles,
} from 'lucide-react'
import type { FileParseResult, UploadedFile } from '@/utils/types/upload'

type StepStatus = 'pending' | 'active' | 'done'

type Props = {
  selectedFile?: UploadedFile | null
  parseResult?: FileParseResult | null
  uploading?: boolean
  onPreviewExample?: () => void
  compact?: boolean
}

const STEPS = [
  {
    icon: FileSearch,
    title: '文档解析',
    desc: '提取文本、表格、公式等内容',
  },
  {
    icon: Sparkles,
    title: '内容理解',
    desc: '识别章节结构与核心概念',
  },
  {
    icon: GitBranch,
    title: '知识映射',
    desc: '关联知识点与学习路径',
  },
  {
    icon: CheckCircle2,
    title: '可视化生成',
    desc: '生成思维导图与知识卡片',
  },
] as const

function resolveStepStatuses(
  selectedFile?: UploadedFile | null,
  parseResult?: FileParseResult | null,
  uploading?: boolean,
): StepStatus[] {
  if (uploading) return ['active', 'pending', 'pending', 'pending']
  if (!selectedFile) return ['pending', 'pending', 'pending', 'pending']

  const ps = parseResult?.status || selectedFile.parseStatus
  if (ps === 'pending') return ['active', 'pending', 'pending', 'pending']
  if (ps === 'processing') return ['done', 'active', 'pending', 'pending']
  if (ps === 'failed') return ['done', 'done', 'active', 'pending']
  if (ps === 'success') return ['done', 'done', 'done', 'done']
  return ['pending', 'pending', 'pending', 'pending']
}

function statusLabel(status: StepStatus, index: number, uploading?: boolean) {
  if (uploading && index === 0) return '上传中'
  if (status === 'pending') return '待上传'
  if (status === 'active') return '处理中'
  return '已完成'
}

export default function AiParsePipelineCard({
  selectedFile,
  parseResult,
  uploading,
  onPreviewExample,
  compact,
}: Props) {
  const statuses = resolveStepStatuses(selectedFile, parseResult, uploading)

  return (
    <section
      className={`shrink-0 rounded-2xl border border-[#E6EEFA] bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)] ${
        compact ? 'p-3.5' : 'p-5'
      }`}
    >
      <div className={`flex items-center justify-between ${compact ? 'mb-2.5' : 'mb-4'}`}>
        <h3 className={`font-bold text-[#0F2A5F] ${compact ? 'text-sm' : 'text-base'}`}>
          AI 解析与知识提取
        </h3>
        {onPreviewExample ? (
          <button
            type="button"
            onClick={onPreviewExample}
            className="text-[11px] font-medium text-[#2563EB] hover:underline"
          >
            示例预览
          </button>
        ) : null}
      </div>

      <div className="space-y-0">
        {STEPS.map((step, index) => {
          const status = statuses[index]
          const Icon = step.icon
          const isLast = index === STEPS.length - 1
          const active = status === 'active'
          const done = status === 'done'
          const iconSize = compact ? 'h-7 w-7' : 'h-9 w-9'
          const connectorH = compact ? 'min-h-[18px]' : 'min-h-[28px]'

          return (
            <div key={step.title} className="flex gap-2.5">
              <div className="flex flex-col items-center">
                <span
                  className={`flex ${iconSize} items-center justify-center rounded-full ${
                    done
                      ? 'bg-[#DCFCE7] text-[#22C55E]'
                      : active
                        ? 'bg-[#EAF3FF] text-[#2563EB]'
                        : 'bg-[#F1F5F9] text-[#94A3B8]'
                  }`}
                >
                  {active ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : done ? (
                    <CheckCircle2 className="h-3.5 w-3.5" />
                  ) : (
                    <Circle className="h-3.5 w-3.5" />
                  )}
                </span>
                {!isLast ? (
                  <span
                    className={`my-0.5 w-0.5 flex-1 ${connectorH} ${
                      done ? 'bg-[#22C55E]' : 'bg-[#E8EEF7]'
                    }`}
                  />
                ) : null}
              </div>

              <div className={`min-w-0 flex-1 ${isLast ? '' : compact ? 'pb-2.5' : 'pb-5'}`}>
                <div className="flex flex-wrap items-center gap-1.5">
                  <Icon className="h-3.5 w-3.5 shrink-0 text-[#64748B]" />
                  <p className={`font-bold text-[#334155] ${compact ? 'text-xs' : 'text-sm'}`}>
                    {step.title}
                  </p>
                  <span
                    className={`rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${
                      done
                        ? 'bg-[#DCFCE7] text-[#22C55E]'
                        : active
                          ? 'bg-[#FFEDD5] text-[#F59E0B]'
                          : 'bg-[#F1F5F9] text-[#94A3B8]'
                    }`}
                  >
                    {statusLabel(status, index, uploading)}
                  </span>
                </div>
                {!compact ? (
                  <p className="mt-1 text-xs leading-relaxed text-[#64748B]">{step.desc}</p>
                ) : null}
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}
