type Props = {
  percent: number
  size?: number
  label?: string
  compact?: boolean
}

export default function ProgressRing({ percent, size = 120, label, compact }: Props) {
  const safe = Math.max(0, Math.min(100, percent))
  const stroke = compact ? 6 : 8
  const radius = (size - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const offset = circumference - (safe / 100) * circumference

  return (
    <div className="relative inline-flex shrink-0 items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#E8EEF7"
          strokeWidth={stroke}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#2563EB"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className="transition-all duration-500"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        <span className={`font-extrabold text-[#0F2A5F] ${compact ? 'text-base' : 'text-xl'}`}>
          {safe}%
        </span>
        {label ? <span className="text-[10px] text-[#64748B]">{label}</span> : null}
      </div>
    </div>
  )
}
