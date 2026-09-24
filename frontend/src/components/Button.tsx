import { type ButtonHTMLAttributes, type ReactNode } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  variant?: 'primary' | 'secondary'
}

export function Button({ children, variant = 'primary', className = '', ...props }: ButtonProps) {
  const styles =
    variant === 'primary'
      ? 'bg-indigo-600 hover:bg-indigo-500 text-white'
      : 'bg-slate-800 hover:bg-slate-700 text-slate-100'
  return (
    <button
      className={`rounded-lg px-4 py-2 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-400 ${styles} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
}
