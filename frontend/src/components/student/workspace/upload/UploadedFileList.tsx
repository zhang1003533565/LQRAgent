import { ChevronLeft, ChevronRight } from 'lucide-react'
import { EmptyState, ErrorState, LoadingSkeleton } from './shared'
import { UploadedFileItem } from './UploadedFileItem'
import type { UploadedFile } from '@/utils/types/upload'

type Props = {
  files: UploadedFile[]
  total: number
  page: number
  totalPages: number
  loading?: boolean
  error?: string | null
  selectedId?: string | null
  batchMode?: boolean
  selectedIds?: string[]
  onSelect: (file: UploadedFile) => void
  onCheck?: (fileId: string, checked: boolean) => void
  onPreview: (file: UploadedFile) => void
  onViewParse: (file: UploadedFile) => void
  onGenerate: (file: UploadedFile) => void
  onRelate: (file: UploadedFile) => void
  onDelete: (file: UploadedFile) => void
  onRetryParse: (file: UploadedFile) => void
  onBatchDelete?: () => void
  onPageChange: (page: number) => void
  onRetry?: () => void
  onUploadClick?: () => void
  actionLoadingId?: string | null
}

export default function UploadedFileList({
  files,
  total,
  page,
  totalPages,
  loading,
  error,
  selectedId,
  batchMode,
  selectedIds = [],
  onSelect,
  onCheck,
  onPreview,
  onViewParse,
  onGenerate,
  onRelate,
  onDelete,
  onRetryParse,
  onBatchDelete,
  onPageChange,
  onRetry,
  onUploadClick,
  actionLoadingId,
}: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-5 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="text-lg font-bold text-[#0F2A5F]">已上传资料</h2>
        <div className="flex items-center gap-3 text-sm text-[#64748B]">
          <span>共 {total} 个</span>
          {batchMode && selectedIds.length > 0 ? (
            <button
              type="button"
              onClick={onBatchDelete}
              className="rounded-lg border border-[#FECACA] px-3 py-1 text-xs font-semibold text-[#EF4444]"
            >
              批量删除 ({selectedIds.length})
            </button>
          ) : null}
        </div>
      </div>

      {loading ? <LoadingSkeleton rows={4} /> : null}
      {error && !loading ? <ErrorState message={error} onRetry={onRetry} /> : null}

      {!loading && !error && files.length === 0 ? (
        <EmptyState
          title="还没有上传学习资料"
          description="上传后 AI 可以帮你整理成学习资源"
          action={
            onUploadClick ? (
              <button
                type="button"
                onClick={onUploadClick}
                className="rounded-xl bg-[#2563EB] px-4 py-2 text-sm font-semibold text-white"
              >
                上传资料
              </button>
            ) : undefined
          }
        />
      ) : null}

      {!loading && !error && files.length > 0 ? (
        <div className="space-y-3">
          {files.map((file) => (
            <UploadedFileItem
              key={file.id}
              file={file}
              selected={selectedId === file.id}
              batchMode={batchMode}
              checked={selectedIds.includes(file.id)}
              onSelect={() => onSelect(file)}
              onCheck={(checked) => onCheck?.(file.id, checked)}
              onPreview={() => onPreview(file)}
              onViewParse={() => onViewParse(file)}
              onGenerate={() => onGenerate(file)}
              onRelate={() => onRelate(file)}
              onDelete={() => onDelete(file)}
              onRetryParse={() => onRetryParse(file)}
              actionLoading={actionLoadingId === file.id}
            />
          ))}
        </div>
      ) : null}

      {totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between border-t border-[#EEF3FA] pt-4">
          <span className="text-xs text-[#64748B]">第 {page} / {totalPages} 页</span>
          <div className="flex gap-1">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => onPageChange(page - 1)}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-[#E6EEFA] disabled:opacity-40"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => onPageChange(page + 1)}
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-[#E6EEFA] disabled:opacity-40"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}
