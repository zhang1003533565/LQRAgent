import { Loader2, Sparkles } from 'lucide-react'
import type { FileParseResult } from '@/utils/types/upload'
import { EmptyState, ErrorState, LoadingSkeleton } from './shared'

type Props = {
  data?: FileParseResult | null
  loading?: boolean
  error?: string | null
  fileName?: string
  onViewFull?: () => void
  onGenerate?: () => void
  onRetry?: () => void
  onRefresh?: () => void
}

export default function ParsePreviewCard({
  data,
  loading,
  error,
  fileName,
  onViewFull,
  onGenerate,
  onRetry,
  onRefresh,
}: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5">
      <h3 className="text-base font-bold text-[#0F2A5F]">AI 解析预览</h3>
      {fileName ? (
        <p className="mt-1 truncate text-xs text-[#64748B]">{fileName}</p>
      ) : null}

      {loading ? <LoadingSkeleton rows={2} /> : null}
      {error && !loading ? <ErrorState message={error} onRetry={onRefresh} /> : null}

      {!loading && !error && !data ? (
        <EmptyState
          title="选择文件查看解析"
          description="点击文件行可在右侧预览 AI 解析结果"
        />
      ) : null}

      {!loading && !error && data ? (
        <div className="mt-3 space-y-3">
          {data.status === 'processing' || data.status === 'pending' ? (
            <div className="flex items-center gap-2 rounded-xl bg-[#FFEDD5] px-3 py-2 text-sm text-[#F59E0B]">
              <Loader2 className="h-4 w-4 animate-spin" />
              AI 正在分析资料内容
            </div>
          ) : null}

          {data.status === 'failed' ? (
            <div className="rounded-xl bg-[#FEE2E2] px-3 py-2 text-sm text-[#EF4444]">
              {data.errorMessage || '解析失败'}
              {onRetry ? (
                <button type="button" onClick={onRetry} className="ml-2 font-semibold underline">
                  重新解析
                </button>
              ) : null}
            </div>
          ) : null}

          {data.summary ? (
            <p className="text-sm leading-relaxed text-[#334155] line-clamp-4">{data.summary}</p>
          ) : null}

          {(data.knowledgePoints || []).length > 0 ? (
            <div>
              <p className="mb-2 text-xs font-semibold text-[#64748B]">识别知识点</p>
              <div className="flex flex-wrap gap-2">
                {data.knowledgePoints!.slice(0, 5).map((kp) => (
                  <span
                    key={kp.id || kp.name}
                    className="rounded-full bg-[#EAF3FF] px-2.5 py-1 text-xs font-medium text-[#2563EB]"
                  >
                    {kp.name}
                    {kp.confidence != null ? ` ${Math.round(kp.confidence)}%` : ''}
                  </span>
                ))}
              </div>
            </div>
          ) : null}

          {(data.suggestedTags || []).length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {data.suggestedTags!.slice(0, 4).map((tag) => (
                <span key={tag} className="rounded-full bg-[#F1F5F9] px-2 py-0.5 text-xs text-[#64748B]">
                  {tag}
                </span>
              ))}
            </div>
          ) : null}

          <div className="flex flex-wrap gap-2 pt-2">
            {onViewFull ? (
              <button
                type="button"
                onClick={onViewFull}
                className="rounded-lg border border-[#D8E4F5] px-3 py-1.5 text-xs font-semibold text-[#2563EB]"
              >
                查看完整解析
              </button>
            ) : null}
            {onGenerate && data.status === 'success' ? (
              <button
                type="button"
                onClick={onGenerate}
                className="inline-flex items-center gap-1 rounded-lg bg-[#2563EB] px-3 py-1.5 text-xs font-semibold text-white"
              >
                <Sparkles className="h-3.5 w-3.5" />
                生成学习资源
              </button>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  )
}
