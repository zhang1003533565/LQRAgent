import type { FileParseResult, StorageUsage, UploadedFile } from '@/utils/types/upload'
import AiParsePipelineCard from './AiParsePipelineCard'
import StorageUsageCard from './StorageUsageCard'
import UploadTipsCard from './UploadTipsCard'

type Props = {
  storage?: StorageUsage | null
  storageLoading?: boolean
  storageError?: string | null
  selectedFile?: UploadedFile | null
  parseResult?: FileParseResult | null
  uploading?: boolean
  onRefreshStorage?: () => void
  onPreviewExample?: () => void
}

export default function UploadAuxPanel({
  storage,
  storageLoading,
  storageError,
  selectedFile,
  parseResult,
  uploading,
  onRefreshStorage,
  onPreviewExample,
}: Props) {
  return (
    <aside className="hidden h-full w-[280px] shrink-0 flex-col overflow-hidden border-l border-[#E6EEFA] bg-[#F8FBFF] px-4 py-5 xl:flex 2xl:w-[300px]">
      <div className="flex h-full flex-col justify-between gap-3 overflow-hidden">
        <AiParsePipelineCard
          selectedFile={selectedFile}
          parseResult={parseResult}
          uploading={uploading}
          onPreviewExample={onPreviewExample}
          compact
        />
        <UploadTipsCard storage={storage} compact />
        <StorageUsageCard
          data={storage}
          loading={storageLoading}
          error={storageError}
          onRetry={onRefreshStorage}
          compact
        />
      </div>
    </aside>
  )
}
