import DevConsoleMain from './DevConsoleMain'
import DevConsoleSidebar from './DevConsoleSidebar'
import DevConsoleTopbar from './DevConsoleTopbar'

export default function DevConsoleShell() {
  return (
    <div className="dev-console-root flex h-screen flex-col overflow-hidden">
      <div className="shrink-0 border-b border-amber-500/30 bg-amber-500/10 px-4 py-1.5 text-center text-xs text-amber-200/90">
        Dev Console：Dashboard 部分指标与服务状态为开发 Mock，仅供本地调试；生产请以真实 API / 监控为准。
      </div>
      <DevConsoleTopbar />
      <div className="flex min-h-0 flex-1">
        <DevConsoleSidebar />
        <DevConsoleMain />
      </div>
    </div>
  )
}
