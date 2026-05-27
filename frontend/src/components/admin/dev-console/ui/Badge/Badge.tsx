import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/components/admin/dev-console/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium',
  {
    variants: {
      variant: {
        default: 'border-console-blue/40 bg-console-blue/15 text-console-blue',
        success: 'border-console-green/40 bg-console-green/15 text-console-green',
        warning: 'border-console-orange/40 bg-console-orange/15 text-console-orange',
        danger: 'border-console-red/40 bg-console-red/15 text-console-red',
        muted: 'border-console-border bg-console-card text-console-muted',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

export interface ConsoleBadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

export function ConsoleBadge({ className, variant, ...props }: ConsoleBadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />
}
