type Props = {
  percent: number
  size?: number
  stroke?: number
  showCenterLabel?: boolean
}

export default function ProgressRing({
  percent,
  size = 100,
  stroke = 7,
  showCenterLabel = true,
}: Props) {
  const safe = Math.max(0, Math.min(100, percent))
  const radius = (size - stroke) / 2
  const c = 2 * Math.PI * radius
  const offset = c - (safe / 100) * c

  return (
    <div className="relative inline-flex shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="#E8EEF7" strokeWidth={stroke} />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#2563EB"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={offset}
        />
      </svg>
      {showCenterLabel ? (
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-lg font-extrabold text-[#0F2A5F]">{safe}%</span>
        </div>
      ) : null}
    </div>
  )
}
