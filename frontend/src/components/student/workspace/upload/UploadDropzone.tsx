import { useRef, useState } from 'react'
import { FolderOpen, UploadCloud } from 'lucide-react'
import { formatBytes } from '@/utils/upload/uploadConstants'
import type { StorageUsage } from '@/utils/types/upload'

type Props = {
  storage?: StorageUsage | null
  disabled?: boolean
  onFilesSelected: (files: File[]) => void
  onValidationError: (message: string) => void
}

const FORMAT_TAGS = ['PDF', 'Word', 'PPT', 'Excel', '图片', '文本']

export default function UploadDropzone({
  storage,
  disabled,
  onFilesSelected,
  onValidationError,
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

      <p className="mt-4 text-center text-xs text-[#94A3B8]">
        {maxSize ? `单文件最大 ${formatBytes(maxSize)}` : null}
        {maxSize && remaining != null ? ' · ' : null}
        {remaining != null ? `剩余可用 ${formatBytes(remaining)}` : null}
      </p>
    </section>
  )
}
