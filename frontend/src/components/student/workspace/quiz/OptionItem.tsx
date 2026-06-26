import { Check, X } from 'lucide-react'

type Props = {
  option: { id: string; label: string; content: string }
  selected: boolean
  submitted?: boolean
  isCorrect?: boolean
  isWrong?: boolean
  onSelect: () => void
}

export default function OptionItem({ option, selected, submitted, isCorrect, isWrong, onSelect }: Props) {
  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={submitted}
      className={`flex min-h-[72px] w-full items-center gap-[18px] rounded-[14px] border px-5 text-left transition-all ${
        isCorrect
          ? 'border-[#22C55E] bg-[#ECFDF5]'
          : isWrong
            ? 'border-[#EF4444] bg-[#FEF2F2]'
            : selected
              ? 'border-[1.5px] border-[#2563EB] bg-[#EFF6FF]'
              : 'border border-[#E6EEFA] bg-white hover:border-[#BFD7FF] hover:bg-[#F8FBFF]'
      }`}
    >
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-base font-bold ${
          isCorrect
            ? 'bg-[#22C55E] text-white'
            : isWrong
              ? 'bg-[#EF4444] text-white'
              : selected
                ? 'bg-[#2563EB] text-white'
                : 'bg-[#F1F5F9] text-[#64748B]'
        }`}
      >
        {option.label}
      </span>
      <span className={`flex-1 text-base ${selected && !submitted ? 'text-[#2563EB]' : 'text-[#334155]'}`}>
        {option.content}
      </span>
      {isCorrect ? <Check className="h-5 w-5 text-[#22C55E]" /> : null}
      {isWrong ? <X className="h-5 w-5 text-[#EF4444]" /> : null}
    </button>
  )
}
