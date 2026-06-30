export function easeOutCubic(t: number): number {
  return 1 - (1 - t) ** 3
}

export function lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t
}

export function lerpAlphaMap(
  from: Map<string, number>,
  to: Map<string, number>,
  t: number,
): Map<string, number> {
  const result = new Map<string, number>()
  const keys = new Set([...from.keys(), ...to.keys()])
  for (const key of keys) {
    const start = from.get(key) ?? to.get(key) ?? 1
    const end = to.get(key) ?? from.get(key) ?? 1
    result.set(key, lerp(start, end, t))
  }
  return result
}
