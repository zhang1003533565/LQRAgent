import { X } from 'lucide-react'
import type { FileParseResult, UploadedFile } from '@/utils/types/upload'
import { formatBytes, formatDateTime } from '@/utils/upload/uploadConstants'
import { LoadingSkeleton } from './shared'

type Props = {
  open: boolean
  file?: UploadedFile | null
  parseResult?: FileParseResult | null
  loading?: boolean
  onClose: () => void
  onGenerate?: () => void
  onRetry?: () => void
}

export default function FileParseDrawer({
  open,
  file,
  parseResult,
  loading,
  onClose,
  onGenerate,
  onRetry,
}: Props) {
  if (!open || !file) return null

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/30" onClick={onClose}>
      <aside
        className="h-full w-full max-w-[520px] overflow-y-auto bg-white p-6 shadow-[0_20px_60px_rgba(15,23,42,0.16)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-[#0F2A5F]">解析详情</h2>
          <button type="button" onClick={onClose} className="rounded-lg p-2 hover:bg-[#F1F5F9]">
            <X className="h-5 w-5 text-[#64748B]" />
          </button>
        </div>

        <div className="mt-4 rounded-xl border border-[#EEF3FA] bg-[#F8FBFF] p-4">
          <p className="font-bold text-[#334155]">{file.name}</p>
          <p className="mt-1 text-xs text-[#64748B]">
            {formatBytes(file.sizeBytes)} · {formatDateTime(file.uploadedAt)}
          </p>
        </div>

        {loading ? <LoadingSkeleton rows={4} /> : null}

        {!loading && parseResult ? (
          <div className="mt-4 space-y-4">
            {parseResult.summary ? (
              <section>
                <h3 className="text-sm font-bold text-[#0F2A5F]">AI 摘要</h3>
                <p className="mt-2 text-sm leading-relaxed text-[#334155]">{parseResult.summary}</p>
              </section>
            ) : null}

            {parseResult.extractedText ? (
              <section>
                <h3 className="text-sm font-bold text-[#0F2A5F]">提取文本</h3>
                <p className="mt-2 max-h-40 overflow-y-auto text-sm text-[#64748B] line-clamp-6">
                  {parseResult.extractedText}
                </p>
              </section>
            ) : null}

            {(parseResult.chapters || []).length > 0 ? (
              <section>
                <h3 className="text-sm font-bold text-[#0F2A5F]">章节结构</h3>
                <ul className="mt-2 space-y-2">
                  {parseResult.chapters!.map((ch) => (
                    <li key={ch.order} className="rounded-lg border border-[#EEF3FA] px-3 py-2 text-sm">
                      <p className="font-semibold text-[#334155]">{ch.title}</p>
                      {ch.summary ? <p className="text-xs text-[#64748B]">{ch.summary}</p> : null}
                    </li>
                  ))}
                </ul>
              </section>
            ) : null}

            {(parseResult.knowledgePoints || []).length > 0 ? (
              <section>
                <h3 className="text-sm font-bold text-[#0F2A5F]">识别知识点</h3>
                <div className="mt-2 flex flex-wrap gap-2">
                  {parseResult.knowledgePoints!.map((kp) => (
                    <span
                      key={kp.id || kp.name}
                      className="rounded-full bg-[#EAF3FF] px-2.5 py-1 text-xs font-medium text-[#2563EB]"
                    >
                      {kp.name}
                    </span>
                  ))}
                </div>
              </section>
            ) : null}

            {(parseResult.suggestedTags || []).length > 0 ? (
              <section>
                <h3 className="text-sm font-bold text-[#0F2A5F]">推荐标签</h3>
                <div className="mt-2 flex flex-wrap gap-2">
                  {parseResult.suggestedTags!.map((tag) => (
                    <span key={tag} className="rounded-full bg-[#F1F5F9] px-2 py-0.5 text-xs text-[#64748B]">
                      {tag}
                    </span>
                  ))}
                </div>
              </section>
            ) : null}

            <div className="flex gap-2 pt-2">
              {onGenerate && parseResult.status === 'success' ? (
                <button
                  type="button"
                  onClick={onGenerate}
                  className="rounded-xl bg-[#2563EB] px-4 py-2 text-sm font-semibold text-white"
                >
                  生成学习资源
                </button>
              ) : null}
              {onRetry && parseResult.status === 'failed' ? (
                <button
                  type="button"
                  onClick={onRetry}
                  className="rounded-xl border border-[#D8E4F5] px-4 py-2 text-sm font-semibold text-[#2563EB]"
                >
                  重新解析
                </button>
              ) : null}
            </div>
          </div>
        ) : null}
      </aside>
    </div>
  )
}
