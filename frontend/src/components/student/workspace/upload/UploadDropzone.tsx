import { useRef, useState } from 'react'
import {
  ClipboardPaste,
  Cloud,
  FolderOpen,
  Monitor,
  ScanLine,
  UploadCloud,
} from 'lucide-react'
import { formatBytes } from '@/utils/upload/uploadConstants'
import type { StorageUsage } from '@/utils/types/upload'

type Props = {
  storage?: StorageUsage | null
  disabled?: boolean
  onFilesSelected: (files: File[]) => void
  onValidationError: (message: string) => void
  onUnsupportedAction?: (label: string) => void
}

const FORMAT_TAGS = ['PDF', 'Word', 'PPT', 'Excel', '图片', '文本']

export default function UploadDropzone({
  storage,
  disabled,
  onFilesSelected,
  onValidationError,
  onUnsupportedAction,
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)

  const maxSize = storage?.maxFileSizeBytes
  const remaining =
    storage && storage.totalBytes > 0 ? storage.totalBytes - storage.usedBytes : null

  const accept = storage?.supportedMimeTypes?.join(',') || undefined

  const handleFiles = (fileList: FileList | null) => {
    if (!fileList?.length) return
    const files = Array.from(fileList)
    for (const file of files) {
      if (maxSize && file.size > maxSize) {
        onValidationError(`文件「${file.name}」超过单文件大小限制`)
        return
      }
      if (remaining != null && remaining > 0 && file.size > remaining) {
        onValidationError('存储空间不足，请删除部分资料后再上传')
        return
      }
    }
    onFilesSelected(files)
  }

  const secondaryOptions = [
    { icon: Monitor, label: '从电脑选择', action: () => inputRef.current?.click() },
    { icon: Cloud, label: '从网盘导入', todo: true },
    { icon: ScanLine, label: '扫描文档', todo: true },
    { icon: ClipboardPaste, label: '粘贴文本', todo: true },
  ] as const

  return (
    <section className="rounded-[18px] border border-[#E6EEFA] bg-white p-6 shadow-[0_8px_24px_rgba(15,23,42,0.04)]">
      <div
        className={`relative flex min-h-[220px] w-full flex-col items-center justify-center rounded-2xl border-2 border-dashed p-8 text-center transition ${
          dragOver
            ? 'border-[#2563EB] bg-[#EAF3FF]'
            : 'border-[#BFD7FF] bg-gradient-to-b from-[#FAFCFF] to-[#F0F6FF]'
        }`}
        onDragOver={(e) => {
          e.preventDefault()
          setDragOver(true)
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragOver(false)
          if (!disabled) handleFiles(e.dataTransfer.files)
        }}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={accept}
          className="hidden"
          onChange={(e) => {
            handleFiles(e.target.files)
            e.target.value = ''
          }}
        />

        <div className="mb-4 flex h-[72px] w-[72px] items-center justify-center rounded-[22px] bg-[#EAF3FF]">
          <UploadCloud className={`h-10 w-10 text-[#2563EB] ${dragOver ? 'scale-110' : ''} transition-transform`} />
        </div>

        <p className="text-xl font-extrabold text-[#0F2A5F]">
          {dragOver ? '松开即可上传' : '拖拽文件到这里，或点击选择文件'}
        </p>

        <div className="mt-3 flex flex-wrap justify-center gap-2">
          {FORMAT_TAGS.map((tag) => (
            <span
              key={tag}
              className="rounded-full bg-white/80 px-2.5 py-0.5 text-xs text-[#64748B] ring-1 ring-[#E6EEFA]"
            >
              {tag}
            </span>
          ))}
        </div>

        <button
          type="button"
          disabled={disabled}
          onClick={() => inputRef.current?.click()}
          className="mt-5 inline-flex h-[44px] items-center gap-2 rounded-xl bg-[#2563EB] px-6 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,0.22)] disabled:opacity-50"
        >
          <FolderOpen className="h-4 w-4" />
          选择文件
        </button>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {secondaryOptions.map(({ icon: Icon, label, action, todo }) => (
          <button
            key={label}
            type="button"
            disabled={disabled && !todo}
            onClick={() => {
              if (todo) {
                onUnsupportedAction?.(label)
                return
              }
              action?.()
            }}
            className="flex flex-col items-center gap-2 rounded-xl border border-[#EEF3FA] bg-[#F8FBFF] px-3 py-4 text-center transition hover:border-[#BFD7FF] hover:bg-white disabled:opacity-60"
          >
            <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-white shadow-sm">
              <Icon className="h-5 w-5 text-[#2563EB]" />
            </span>
            <span className="text-xs font-medium text-[#334155]">{label}</span>
          </button>
        ))}
      </div>

      <p className="mt-4 text-center text-xs text-[#94A3B8]">
        {maxSize ? `单文件最大 ${formatBytes(maxSize)}` : null}
        {maxSize && remaining != null ? ' · ' : null}
        {remaining != null ? `剩余可用 ${formatBytes(remaining)}` : null}
      </p>
    </section>
  )
}
