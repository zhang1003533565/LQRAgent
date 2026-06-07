import React from 'react'

interface NewChatButtonProps {
  onClick: () => void
  loading?: boolean
}

/**
 * 新建聊天按钮组件
 */
export const NewChatButton: React.FC<NewChatButtonProps> = ({
  onClick,
  loading = false,
}) => {
  return (
    <button
      onClick={onClick}
      disabled={loading}
      className="w-full flex items-center justify-center gap-2 px-4 py-2.5
        bg-blue-500 text-white rounded-lg font-medium text-sm
        hover:bg-blue-600 active:bg-blue-700
        disabled:opacity-50 disabled:cursor-not-allowed
        transition-colors duration-150"
    >
      {loading ? (
        <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />
      ) : (
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
        </svg>
      )}
      <span>新建对话</span>
    </button>
  )
}

export default NewChatButton
