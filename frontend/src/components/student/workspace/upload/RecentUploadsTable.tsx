import {
  ChevronRight,
  Eye,
  FileText,
  MoreHorizontal,
  Sparkles,
} from 'lucide-react'
import { formatBytes, formatDateTime } from '@/utils/upload/uploadConstants'
import { EmptyState, ErrorState, LoadingSkeleton } from './shared'
import type { ParseStatus, UploadedFile } from '@/utils/types/upload'

const TYPE_LABEL: Record<string, string> = {
  pdf: 'PDF',
  doc: 'Word',
  docx: 'Word',
  ppt: 'PPT',
  pptx: 'PPT',
  xls: 'Excel',
  xlsx: 'Excel',
  png: '图片',
  jpg: '图片',
  jpeg: '图片',
  md: '文本',
  txt: '文本',
}

const PARSE_STYLE: Record<ParseStatus, string> = {
  pending: 'bg-[#F1F5F9] text-[#64748B]',
  processing: 'bg-[#FFEDD5] text-[#F59E0B]',
  success: 'bg-[#DCFCE7] text-[#22C55E]',
  failed: 'bg-[#FEE2E2] text-[#EF4444]',
}

const PARSE_LABEL: Record<ParseStatus, string> = {
  pending: '待解析',
  processing: '解析中',
  success: '解析完成',
  failed: '解析失败',
}

function fileIconStyle(name: string) {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'bg-[#FEE2E2] text-[#EF4444]'
  if (['doc', 'docx'].includes(ext)) return 'bg-[#DBEAFE] text-[#2563EB]'
  if (['xls', 'xlsx'].includes(ext)) return 'bg-[#DCFCE7] text-[#22C55E]'
  if (['ppt', 'pptx'].includes(ext)) return 'bg-[#FFEDD5] text-[#F59E0B]'
  return 'bg-[#EAF3FF] text-[#2563EB]'
}

function fileIconLabel(name: string) {
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'PDF'
  if (['doc', 'docx'].includes(ext)) return 'W'
  if (['ppt', 'pptx'].includes(ext)) return 'P'
  if (['xls', 'xlsx'].includes(ext)) return 'X'
  return ext.slice(0, 2).toUpperCase() || 'F'
}

type Props = {
  files: UploadedFile[]
  total: number
  loading?: boolean
  error?: string | null
  selectedId?: string | null
  showAll?: boolean
  onSelect: (file: UploadedFile) => void
  onViewAll?: () => void
  onPreview: (file: UploadedFile) => void
  onViewParse: (file: UploadedFile) => void
  onGenerate: (file: UploadedFile) => void
  onRetry?: () => void
}

export default function RecentUploadsTable({
  files,
  total,
  loading,
  error,
  selectedId,
  showAll,
  onSelect,
  onViewAll,
  onPreview,
  onViewParse,
  onGenerate,
  onRetry,
}: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="flex items-center justify-between border-b border-[#EEF3FA] px-5 py-4">
        <h2 className="text-lg font-bold text-[#0F2A5F]">最近上传</h2>
        {!showAll && total > files.length ? (
          <button
            type="button"
            onClick={onViewAll}
            className="inline-flex items-center gap-0.5 text-sm font-medium text-[#2563EB] hover:underline"
          >
            查看全部记录
            <ChevronRight className="h-4 w-4" />
          </button>
        ) : (
          <span className="text-sm text-[#64748B]">共 {total} 个</span>
        )}
      </div>

      {loading ? (
        <div className="p-5">
          <LoadingSkeleton rows={4} />
        </div>
      ) : null}

      {error && !loading ? (
        <div className="p-5">
          <ErrorState message={error} onRetry={onRetry} />
        </div>
      ) : null}

      {!loading && !error && files.length === 0 ? (
        <EmptyState
          title="还没有上传记录"
          description="上传资料后，AI 将自动解析并映射知识点"
        />
      ) : null}

      {!loading && !error && files.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead>
              <tr className="border-b border-[#EEF3FA] bg-[#F8FBFF] text-xs font-semibold text-[#64748B]">
                <th className="px-5 py-3 font-semibold">文件名</th>
                <th className="px-3 py-3 font-semibold">类型</th>
                <th className="px-3 py-3 font-semibold">大小</th>
                <th className="px-3 py-3 font-semibold">知识点</th>
                <th className="px-3 py-3 font-semibold">状态</th>
                <th className="px-3 py-3 font-semibold">上传时间</th>
                <th className="px-5 py-3 text-right font-semibold">操作</th>
              </tr>
            </thead>
            <tbody>
              {files.map((file) => {
                const ext = file.extension || file.name.split('.').pop()?.toLowerCase() || ''
                const parseStatus = file.parseStatus || 'pending'
                const kpCount = file.relatedKnowledgePointIds?.length ?? 0

                return (
                  <tr
                    key={file.id}
                    onClick={() => onSelect(file)}
                    className={`cursor-pointer border-b border-[#F1F5F9] transition last:border-0 hover:bg-[#F8FBFF] ${
                      selectedId === file.id ? 'bg-[#EAF3FF]/60' : ''
                    }`}
                  >
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-3">
                        <span
                          className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-xs font-bold ${fileIconStyle(file.name)}`}
                        >
                          {fileIconLabel(file.name)}
                        </span>
                        <span className="max-w-[200px] truncate font-semibold text-[#334155]">
                          {file.name}
                        </span>
                      </div>
                    </td>
                    <td className="px-3 py-3.5 text-[#64748B]">
                      {TYPE_LABEL[ext] || file.sourceType || '其他'}
                    </td>
                    <td className="px-3 py-3.5 text-[#64748B]">{formatBytes(file.sizeBytes)}</td>
                    <td className="px-3 py-3.5 text-[#64748B]">
                      {kpCount > 0 ? `${kpCount} 个` : '—'}
                    </td>
                    <td className="px-3 py-3.5">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${PARSE_STYLE[parseStatus]}`}
                      >
                        {PARSE_LABEL[parseStatus]}
                      </span>
                    </td>
                    <td className="px-3 py-3.5 text-[#64748B]">
                      {formatDateTime(file.uploadedAt)}
                    </td>
                    <td className="px-5 py-3.5">
                      <div
                        className="flex items-center justify-end gap-1"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <button
                          type="button"
                          title="预览"
                          onClick={() => onPreview(file)}
                          className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]"
                        >
                          <Eye className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          title="查看解析"
                          onClick={() => onViewParse(file)}
                          className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]"
                        >
                          <FileText className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          title="生成资源"
                          onClick={() => onGenerate(file)}
                          className="rounded-lg p-2 text-[#2563EB] hover:bg-[#EAF3FF]"
                        >
                          <Sparkles className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]"
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  )
}
