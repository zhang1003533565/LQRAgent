import DevConsoleMain from './DevConsoleMain'
import DevConsoleSidebar from './DevConsoleSidebar'
import DevConsoleTopbar from './DevConsoleTopbar'

export default function DevConsoleShell() {
  return (
    <div className="dev-console-root flex h-screen flex-col overflow-hidden">
      <DevConsoleTopbar />
      <div className="flex min-h-0 flex-1">
        <DevConsoleSidebar />
        <DevConsoleMain />
      </div>
    </div>
  )
}
