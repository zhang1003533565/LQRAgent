import { Crosshair, Maximize2, Minus, Plus, RefreshCw, Target } from 'lucide-react'
import styles from '@/pages/student/KnowledgeGraphPage.module.css'

interface GraphControlsProps {
  onFitView: () => void
  onResetLayout: () => void
  onToggleFullscreen: () => void
  onZoomIn: () => void
  onZoomOut: () => void
  onResetZoom: () => void
}

export default function GraphControls({
  onFitView,
  onResetLayout,
  onToggleFullscreen,
  onZoomIn,
  onZoomOut,
  onResetZoom,
}: GraphControlsProps) {
  return (
    <>
      <div className={styles.zoomControls}>
        <button type="button" onClick={onZoomIn} aria-label="放大"><Plus size={14} strokeWidth={1.5} /></button>
        <button type="button" onClick={onZoomOut} aria-label="缩小"><Minus size={14} strokeWidth={1.5} /></button>
        <button type="button" onClick={onResetZoom} aria-label="重置缩放"><Crosshair size={14} strokeWidth={1.5} /></button>
      </div>
      <div className={styles.bottomControls}>
        <button type="button" onClick={onFitView} aria-label="适应屏幕"><Target size={14} strokeWidth={1.5} /></button>
        <button type="button" onClick={onResetLayout} aria-label="重置布局"><RefreshCw size={14} strokeWidth={1.5} /></button>
        <button type="button" onClick={onToggleFullscreen} aria-label="全屏"><Maximize2 size={14} strokeWidth={1.5} /></button>
      </div>
    </>
  )
}
