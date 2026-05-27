import { useEffect, useState } from 'react'
import { motion, useSpring, useTransform } from 'framer-motion'

interface AnimatedNumberProps {
  value: number
  format?: (n: number) => string
  className?: string
}

export default function AnimatedNumber({ value, format, className }: AnimatedNumberProps) {
  const spring = useSpring(value, { stiffness: 80, damping: 20 })
  const display = useTransform(spring, (v) => (format ? format(v) : Math.round(v).toLocaleString()))
  const [text, setText] = useState('')

  useEffect(() => {
    spring.set(value)
  }, [value, spring])

  useEffect(() => {
    const unsub = display.on('change', (v) => setText(String(v)))
    return () => unsub()
  }, [display])

  return (
    <motion.span className={className} layout>
      {text}
    </motion.span>
  )
}
