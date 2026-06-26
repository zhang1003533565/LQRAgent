import {
  Eye,
  FileText,
  Link2,
  Loader2,
  MoreHorizontal,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { formatBytes, formatDateTime } from '@/utils/upload/uploadConstants'
import type { ParseStatus, SourceType, UploadedFile } from '@/utils/types/upload'

const STATUS_STYLE: Record<string, string> = {
  uploading: 'bg-[#EAF3FF] text-[#2563EB]',
  uploaded: 'bg-[#EAF3FF] text-[#2563EB]',
  processing: 'bg-[#FFEDD5] text-[#F59E0B]',
  parsed: 'bg-[#DCFCE7] text-[#22C55E]',
  failed: 'bg-[#FEE2E2] text-[#EF4444]',
  deleted: 'bg-[#F1F5F9] text-[#94A3B8]',
}

const PARSE_LABEL: Record<ParseStatus, string> = {
  pending: '待解析',
  processing: '解析中',
  success: '已解析',
  failed: '解析失败',
}

function iconBg(sourceType?: SourceType) {
  switch (sourceType) {
    case 'image':
      return 'bg-[#FCE7F3] text-[#EC4899]'
    case 'video':
      return 'bg-[#EDE9FE] text-[#8B5CF6]'
    case 'audio':
      return 'bg-[#CFFAFE] text-[#06B6D4]'
    case 'code':
      return 'bg-[#E0E7FF] text-[#4F46E5]'
    default:
      return 'bg-[#EAF3FF] text-[#2563EB]'
  }
}

type ItemProps = {
  file: UploadedFile
  selected?: boolean
  batchMode?: boolean
  checked?: boolean
  onSelect: () => void
  onCheck?: (checked: boolean) => void
  onPreview: () => void
  onViewParse: () => void
  onGenerate: () => void
  onRelate: () => void
  onDelete: () => void
  onRetryParse: () => void
  actionLoading?: boolean
}

export function UploadedFileItem({
  file,
  selected,
  batchMode,
  checked,
  onSelect,
  onCheck,
  onPreview,
  onViewParse,
  onGenerate,
  onRelate,
  onDelete,
  onRetryParse,
  actionLoading,
}: ItemProps) {
  const statusClass = STATUS_STYLE[file.status] || STATUS_STYLE.uploaded
  const parseLabel = file.parseStatus ? PARSE_LABEL[file.parseStatus] : '—'

  return (
    <div
      className={`flex min-h-[76px] cursor-pointer items-center gap-3 rounded-[14px] border px-4 py-3 transition ${
        selected
          ? 'border-[#2563EB] bg-[#EAF3FF]'
          : 'border-[#EEF3FA] bg-white hover:border-[#BFD7FF] hover:bg-[#F8FBFF]'
      }`}
      onClick={onSelect}
    >
      {batchMode ? (
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onCheck?.(e.target.checked)}
          onClick={(e) => e.stopPropagation()}
          className="h-4 w-4 rounded border-[#CBD5E1]"
        />
      ) : null}

      <div className={`flex h-[42px] w-[42px] items-center justify-center rounded-[14px] ${iconBg(file.sourceType)}`}>
        <FileText className="h-5 w-5" />
      </div>

      <div className="min-w-0 flex-1">
        <p className="truncate text-[15px] font-bold text-[#334155]">{file.name}</p>
        <div className="mt-1 flex flex-wrap gap-2 text-xs text-[#64748B]">
          <span>{formatBytes(file.sizeBytes)}</span>
          <span>{formatDateTime(file.uploadedAt)}</span>
          {file.extension ? <span>{file.extension}</span> : null}
          {(file.tags || []).slice(0, 2).map((tag) => (
            <span key={tag} className="rounded-full bg-[#F1F5F9] px-2 py-0.5">{tag}</span>
          ))}
        </div>
        {file.parseStatus === 'failed' && file.parseError ? (
          <p className="mt-1 text-xs text-[#EF4444]">{file.parseError}</p>
        ) : null}
      </div>

      <div className="flex flex-col items-end gap-1">
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusClass}`}>
          {file.parseStatus === 'processing' ? (
            <span className="inline-flex items-center gap-1">
              <Loader2 className="h-3 w-3 animate-spin" />
              AI 解析中
            </span>
          ) : (
            parseLabel
          )}
        </span>
        {file.progress != null && file.status === 'processing' ? (
          <span className="text-xs text-[#64748B]">{file.progress}%</span>
        ) : null}
      </div>

      {!batchMode ? (
        <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
          <button type="button" title="预览" onClick={onPreview} className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]">
            <Eye className="h-4 w-4" />
          </button>
          <button type="button" title="查看解析" onClick={onViewParse} className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]">
            <FileText className="h-4 w-4" />
          </button>
          <button
            type="button"
            title="生成资源"
            disabled={actionLoading}
            onClick={onGenerate}
            className="rounded-lg p-2 text-[#2563EB] hover:bg-[#EAF3FF] disabled:opacity-50"
          >
            <Sparkles className="h-4 w-4" />
          </button>
          <button type="button" title="关联路径" onClick={onRelate} className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]">
            <Link2 className="h-4 w-4" />
          </button>
          {file.parseStatus === 'failed' ? (
            <button type="button" title="重新解析" onClick={onRetryParse} className="rounded-lg p-2 text-[#F59E0B] hover:bg-[#FFEDD5]">
              <Loader2 className="h-4 w-4" />
            </button>
          ) : null}
          <button type="button" title="删除" onClick={onDelete} className="rounded-lg p-2 text-[#EF4444] hover:bg-[#FEE2E2]">
            <Trash2 className="h-4 w-4" />
          </button>
          <button type="button" className="rounded-lg p-2 text-[#64748B] hover:bg-[#F1F5F9]">
            <MoreHorizontal className="h-4 w-4" />
          </button>
        </div>
      ) : null}
    </div>
  )
}
