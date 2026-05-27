import { useState } from 'react'
import { motion } from 'framer-motion'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { DEV_CONSOLE_NAV } from '@/components/admin/dev-console/constants/dev-console-nav'
import { useDevConsoleStore } from '@/components/admin/dev-console/store/devConsoleStore'
import { cn } from '@/components/admin/dev-console/lib/utils'
import type { DevConsoleNavId } from '@/components/admin/dev-console/types/dev-console'

export default function DevConsoleSidebar() {
  const activeNav = useDevConsoleStore((s) => s.activeNav)
  const setActiveNav = useDevConsoleStore((s) => s.setActiveNav)
  const [collapsedGroups, setCollapsedGroups] = useState<Record<string, boolean>>({})

  const toggleGroup = (title: string) => {
    setCollapsedGroups((prev) => ({ ...prev, [title]: !prev[title] }))
  }

  return (
    <aside className="flex w-56 shrink-0 flex-col border-r border-console-border bg-console-card/40">
      <nav className="flex-1 space-y-5 overflow-y-auto p-3">
        {DEV_CONSOLE_NAV.map((group) => {
          const collapsed = collapsedGroups[group.title] ?? false
          return (
            <div key={group.title}>
              {/* 组标题 — 可点击折叠 */}
              <button
                type="button"
                onClick={() => toggleGroup(group.title)}
                className="mb-1.5 flex w-full items-center gap-1.5 px-2 text-sm font-bold uppercase tracking-wider text-console-muted hover:text-console-text"
              >
                {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                {group.title}
              </button>

              {/* 子项 */}
              {!collapsed && (
                <ul className="space-y-0.5">
                  {group.items.map((item) => {
                    const Icon = item.icon
                    const active = activeNav === item.id
                    return (
                      <li key={item.id}>
                        <motion.button
                          type="button"
                          whileHover={{ x: 2 }}
                          className={cn(
                            'flex w-full items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors',
                            active
                              ? 'bg-console-blue/15 text-console-blue'
                              : 'text-console-muted hover:bg-console-border/30 hover:text-console-text',
                          )}
                          onClick={() => setActiveNav(item.id as DevConsoleNavId)}
                        >
                          <Icon className="h-4 w-4 shrink-0" />
                          <span className="truncate">{item.label}</span>
                        </motion.button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          )
        })}
      </nav>
      <div className="border-t border-console-border p-3 text-[10px] text-console-muted">
        <p>LQRAgent v1.0.0</p>
      </div>
    </aside>
  )
}
