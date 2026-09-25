/** Deterministic gradient avatar with initials + optional online ring. */

const GRADIENTS = [
  'from-indigo-500 to-violet-600',
  'from-rose-500 to-orange-500',
  'from-emerald-500 to-teal-600',
  'from-sky-500 to-blue-700',
  'from-fuchsia-500 to-pink-600',
  'from-amber-500 to-yellow-600',
]

function hashCode(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

export function Avatar({
  name,
  id,
  size = 'md',
  online,
  ring,
  src,
  className = '',
}: {
  name: string
  id: string
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  online?: boolean
  /** Story ring styling */
  ring?: boolean
  /** App-relative photo URL (e.g. /media/abc.jpg). Falls back to gradient initials. */
  src?: string | null
  className?: string
}) {
  const sizes = {
    xs: 'h-6 w-6 text-[10px]',
    sm: 'h-8 w-8 text-xs',
    md: 'h-10 w-10 text-sm',
    lg: 'h-14 w-14 text-lg',
    xl: 'h-24 w-24 text-3xl',
  }
  const pixels = { xs: 24, sm: 32, md: 40, lg: 56, xl: 96 }
  const grad = GRADIENTS[hashCode(id) % GRADIENTS.length]
  const initials = name
    .split(' ')
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
  const ringCls = ring ? 'ring-2 ring-indigo-400 ring-offset-2 ring-offset-[var(--bg)]' : ''

  return (
    <span className={`relative inline-block shrink-0 ${className}`}>
      {src ? (
        <img
          src={src}
          alt={name}
          width={pixels[size]}
          height={pixels[size]}
          loading="lazy"
          className={`inline-block rounded-full object-cover ${sizes[size].split(' ')[0]} ${sizes[size].split(' ')[1]} ${ringCls}`}
        />
      ) : (
        <span
          className={`inline-flex items-center justify-center rounded-full bg-gradient-to-br font-semibold text-white ${grad} ${sizes[size]} ${ringCls}`}
          aria-hidden="true"
        >
          {initials}
        </span>
      )}
      {online !== undefined && (
        <span
          className={`absolute bottom-0 right-0 block rounded-full border-2 border-[var(--surface)] ${
            online ? 'bg-emerald-500' : 'bg-slate-400'
          } ${size === 'xs' || size === 'sm' ? 'h-2.5 w-2.5' : 'h-3 w-3'}`}
          title={online ? 'Online' : 'Offline'}
        />
      )}
    </span>
  )
}
