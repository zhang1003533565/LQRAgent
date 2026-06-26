const FAVORITES_KEY = 'lqragent.resource.favorites'

function readIds(): string[] {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

export function getResourceFavoriteIds(): Set<string> {
  return new Set(readIds())
}

export function setResourceFavorite(resourceId: string, favorite: boolean) {
  const ids = new Set(readIds())
  if (favorite) ids.add(resourceId)
  else ids.delete(resourceId)
  localStorage.setItem(FAVORITES_KEY, JSON.stringify(Array.from(ids)))
}
