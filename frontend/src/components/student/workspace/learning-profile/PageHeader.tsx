import { Download, RefreshCw } from 'lucide-react'

type Props = {
  refreshing?: boolean
  exporting?: boolean
  onRefresh: () => void
  onExport: () => void
}

export default function PageHeader({ refreshing, exporting, onRefresh, onExport }: Props) {
  return (
    <header className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
      <div>
        <h1 className="text-[34px] font-extrabold leading-[1.2] text-[#0F2A5F]">学习画像</h1>
        <p className="mt-1.5 text-sm text-[#64748B]">
          基于学习数据生成的多维能力画像与成长分析
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={onRefresh}
          disabled={refreshing}
          className="inline-flex h-[42px] items-center gap-2 rounded-[10px] border border-[#D8E4F5] bg-white px-4 text-sm font-semibold text-[#2563EB]"
        >
          <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />
          刷新画像
        </button>
        <button
          type="button"
          onClick={onExport}
          disabled={exporting}
          className="inline-flex h-[42px] items-center gap-2 rounded-[10px] bg-gradient-to-br from-[#3B82F6] to-[#2563EB] px-4 text-sm font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,0.18)] disabled:opacity-50"
        >
          <Download className="h-4 w-4" />
          导出报告
        </button>
      </div>
    </header>
  )
}
