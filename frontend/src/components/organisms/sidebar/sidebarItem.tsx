'use client'

import Link from 'next/link'
import { LucideIcon } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'

interface SidebarItemProps {
  label: string
  href: string
  icon: LucideIcon
  isActive?: boolean
  disabled?: boolean
  disabledTooltip?: string
}

export function SidebarItem({
  label,
  href,
  icon: Icon,
  isActive = false,
  disabled = false,
  disabledTooltip,
}: SidebarItemProps) {
  const { isCollapsed, closeMobile } = useSidebar()

  const content = (
    <div
      className={`flex items-center gap-3 px-3 py-3 rounded-xl transition-all duration-200 outline-none font-['Linden_Hill',serif] ${
        isCollapsed ? 'justify-center' : 'justify-start'
      } ${
        disabled
          ? 'opacity-40 cursor-not-allowed text-gray-400'
          : isActive
            ? 'bg-blue-50 text-blue-400 font-semibold shadow-xs'
            : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900 cursor-pointer focus-visible:ring-2 focus-visible:ring-blue-400'
      }`}
    >
      <Icon
        className={`w-5 h-5 shrink-0 ${
          disabled
            ? 'text-gray-400'
            : isActive
              ? 'text-blue-400'
              : 'text-gray-500 group-hover:text-gray-700'
        }`}
      />
      {!isCollapsed && (
        <span className="truncate text-sm font-medium">{label}</span>
      )}
    </div>
  )

  const tooltipText = disabled ? disabledTooltip || label : label

  return (
    <div className="relative group w-full group-hover:z-50">
      {disabled ? (
        <div aria-disabled="true" aria-label={tooltipText}>
          {content}
        </div>
      ) : (
        <Link
          href={href}
          onClick={closeMobile}
          aria-label={label}
          aria-current={isActive ? 'page' : undefined}
        >
          {content}
        </Link>
      )}

      {/* Tooltip flutuante exibido no hover */}
      {(isCollapsed || disabled) && (
        <div
          role="tooltip"
          className="hidden md:flex items-center absolute left-full top-1/2 -translate-y-1/2 ml-3 px-3 py-1.5 bg-gray-900/95 backdrop-blur-xs text-white text-xs font-medium rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none shadow-xl border border-gray-800 z-[999] scale-95 group-hover:scale-100"
        >
          <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900/95" />
          {tooltipText}
        </div>
      )}
    </div>
  )
}
