import React, { useState, useCallback } from 'react'

interface SessionSearchProps {
  onSearch: (keyword: string) => void
  onClear: () => void
  placeholder?: string
}

/**
 * 会话搜索组件
 */
export const SessionSearch: React.FC<SessionSearchProps> = ({
  onSearch,
  onClear,
  placeholder = '搜索会话...',
}) => {
  const [keyword, setKeyword] = useState('')

  const handleSearch = useCallback(() => {
    if (keyword.trim()) {
      onSearch(keyword.trim())
    } else {
      onClear()
    }
  }, [keyword, onSearch, onClear])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch()
    }
  }

  const handleClear = () => {
    setKeyword('')
    onClear()
  }

  return (
    <div className="relative px-2 py-2">
      <div className="relative">
        <svg
          className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="w-full pl-9 pr-8 py-2 text-sm bg-gray-100 rounded-lg
            focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white
            transition-colors"
        />
        {keyword && (
          <button
            onClick={handleClear}
            className="absolute right-2 top-1/2 -translate-y-1/2 p-1 hover:bg-gray-200 rounded"
          >
            <svg className="w-3.5 h-3.5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>
    </div>
  )
}

export default SessionSearch
