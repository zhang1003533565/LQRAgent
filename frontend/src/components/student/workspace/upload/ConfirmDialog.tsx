type Props = {
  open: boolean
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = '确认',
  cancelLabel = '取消',
  loading,
  onConfirm,
  onCancel,
}: Props) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" onClick={onCancel}>
      <div
        className="w-full max-w-sm rounded-2xl border border-[#E6EEFA] bg-white p-6 shadow-[0_20px_60px_rgba(15,23,42,0.16)]"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="text-lg font-bold text-[#0F2A5F]">{title}</h3>
        {description ? <p className="mt-2 text-sm text-[#64748B]">{description}</p> : null}
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-[10px] border border-[#D8E4F5] px-4 py-2 text-sm font-semibold text-[#64748B]"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            disabled={loading}
            onClick={onConfirm}
            className="rounded-[10px] bg-[#EF4444] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
