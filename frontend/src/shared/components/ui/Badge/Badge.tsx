import type { ReactNode } from 'react'
import styles from './Badge.module.css'

export type BadgeVariant = 'default' | 'success' | 'warn' | 'muted'

export interface BadgeProps {
  children: ReactNode
  variant?: BadgeVariant
}

export default function Badge({ children, variant = 'default' }: BadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[variant]}`}>{children}</span>
  )
}
