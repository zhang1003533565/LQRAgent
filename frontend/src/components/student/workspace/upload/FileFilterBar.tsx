import { RotateCcw, Search } from 'lucide-react'
import type { ParseStatus, SourceType, UploadedFileSort } from '@/utils/types/upload'

type Props = {
  keyword: string
  sourceType: SourceType | 'all'
  parseStatus: ParseStatus | 'all'
  learningPathId: string
  sort: UploadedFileSort
  pathOptions: Array<{ id: string; title: string }>
  onKeywordChange: (v: string) => void
  onSourceTypeChange: (v: SourceType | 'all') => void
  onParseStatusChange: (v: ParseStatus | 'all') => void
  onLearningPathChange: (v: string) => void
  onSortChange: (v: UploadedFileSort) => void
  onReset: () => void
}

const SOURCE_OPTIONS: Array<{ value: SourceType | 'all'; label: string }> = [
  { value: 'all', label: '全部类型' },
  { value: 'document', label: '文档' },
  { value: 'image', label: '图片' },
  { value: 'audio', label: '音频' },
  { value: 'video', label: '视频' },
  { value: 'code', label: '代码' },
  { value: 'other', label: '其他' },
]

const PARSE_OPTIONS: Array<{ value: ParseStatus | 'all'; label: string }> = [
  { value: 'all', label: '全部状态' },
  { value: 'pending', label: '待解析' },
  { value: 'processing', label: '解析中' },
  { value: 'success', label: '已解析' },
  { value: 'failed', label: '解析失败' },
]

export default function FileFilterBar({
  keyword,
  sourceType,
  parseStatus,
  learningPathId,
  sort,
  pathOptions,
  onKeywordChange,
  onSourceTypeChange,
  onParseStatusChange,
  onLearningPathChange,
  onSortChange,
  onReset,
}: Props) {
  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#94A3B8]" />
          <input
            value={keyword}
            onChange={(e) => onKeywordChange(e.target.value)}
            placeholder="搜索文件名、标签或知识点..."
            className="h-10 w-full rounded-xl border border-[#E6EEFA] bg-[#F8FBFF] pl-10 pr-4 text-sm outline-none focus:border-[#93C5FD]"
          />
        </div>
        <select
          value={sourceType}
          onChange={(e) => onSourceTypeChange(e.target.value as SourceType | 'all')}
          className="h-10 rounded-xl border border-[#E6EEFA] bg-white px-3 text-sm"
        >
          {SOURCE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
        <select
          value={parseStatus}
          onChange={(e) => onParseStatusChange(e.target.value as ParseStatus | 'all')}
          className="h-10 rounded-xl border border-[#E6EEFA] bg-white px-3 text-sm"
        >
          {PARSE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
        <select
          value={learningPathId}
          onChange={(e) => onLearningPathChange(e.target.value)}
          className="h-10 rounded-xl border border-[#E6EEFA] bg-white px-3 text-sm"
        >
          <option value="all">全部路径</option>
          <option value="current">当前路径</option>
          {pathOptions.map((p) => (
            <option key={p.id} value={p.id}>{p.title}</option>
          ))}
        </select>
        <select
          value={sort}
          onChange={(e) => onSortChange(e.target.value as UploadedFileSort)}
          className="h-10 rounded-xl border border-[#E6EEFA] bg-white px-3 text-sm"
        >
          <option value="uploadedAt">最近上传</option>
          <option value="name">文件名</option>
          <option value="size">文件大小</option>
          <option value="parseStatus">解析状态</option>
        </select>
        <button
          type="button"
          onClick={onReset}
          className="inline-flex h-10 items-center gap-1 rounded-xl border border-[#D8E4F5] px-3 text-sm text-[#64748B]"
        >
          <RotateCcw className="h-4 w-4" />
          重置
        </button>
      </div>
    </section>
  )
}
