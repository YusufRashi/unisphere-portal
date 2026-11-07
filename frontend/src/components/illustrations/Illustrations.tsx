// Small decorative scenes drawn with the theme's colour classes, so they follow light and dark mode. They are
// always aria-hidden: the text next to them says what they show.

function Sparkle({ x, y, size = 8, className = 'fill-brand-300' }: { x: number; y: number; size?: number; className?: string }) {
  const s = size
  return (
    <path
      className={className}
      d={`M${x} ${y - s} Q${x + s * 0.15} ${y - s * 0.15} ${x + s} ${y} Q${x + s * 0.15} ${y + s * 0.15} ${x} ${y + s} Q${x - s * 0.15} ${y + s * 0.15} ${x - s} ${y} Q${x - s * 0.15} ${y - s * 0.15} ${x} ${y - s}Z`}
    />
  )
}

/** Two stacked sheets of paper on a soft backdrop; EmptyState sets its icon on top. */
export function EmptySheets({ className }: { className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 144 96" className={className}>
      <ellipse cx="72" cy="52" rx="62" ry="40" className="fill-brand-50" />
      <rect x="30" y="20" width="62" height="50" rx="9" transform="rotate(-9 61 45)" strokeWidth="1.5" className="fill-surface stroke-brand-200" />
      <rect x="46" y="24" width="62" height="52" rx="9" strokeWidth="1.5" className="fill-surface stroke-brand-200" />
      <rect x="56" y="35" width="30" height="5" rx="2.5" className="fill-brand-200" />
      <rect x="56" y="46" width="42" height="4" rx="2" className="fill-brand-100" />
      <rect x="56" y="55" width="34" height="4" rx="2" className="fill-brand-100" />
      <Sparkle x={20} y={24} size={7} />
      <circle cx="124" cy="20" r="3" className="fill-brand-200" />
      <circle cx="14" cy="68" r="2" className="fill-brand-300" />
    </svg>
  )
}

/** A folded map with a dashed trail that ends at a question-mark pin. */
export function LostMap({ className }: { className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 280 180" className={className}>
      <ellipse cx="140" cy="100" rx="130" ry="74" className="fill-brand-50" />
      <g strokeWidth="1.5" strokeLinejoin="round" className="stroke-brand-200">
        <polygon points="50,48 110,36 110,146 50,158" className="fill-surface" />
        <polygon points="110,36 170,48 170,158 110,146" className="fill-brand-100" />
        <polygon points="170,48 230,36 230,146 170,158" className="fill-surface" />
      </g>
      <path
        d="M70 130 C95 120 88 86 122 92 S166 122 194 86"
        fill="none"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeDasharray="4 6"
        className="stroke-brand-400"
      />
      <circle cx="70" cy="130" r="4.5" className="fill-brand-400" />
      <path d="M196 84 C186 72 180 64 180 55 A16 16 0 1 1 212 55 C212 64 206 72 196 84Z" className="fill-accent" />
      <circle cx="196" cy="55" r="9" className="fill-on-accent" />
      <text x="196" y="60" textAnchor="middle" className="fill-accent text-[13px] font-bold">?</text>
      <Sparkle x={34} y={40} size={8} />
      <Sparkle x={250} y={124} size={6} className="fill-brand-200" />
      <circle cx="246" cy="30" r="3" className="fill-brand-200" />
      <circle cx="28" cy="120" r="2.5" className="fill-brand-300" />
    </svg>
  )
}

/** A closed door with a padlock in front of it. */
export function LockedDoor({ className }: { className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 280 180" className={className}>
      <ellipse cx="140" cy="100" rx="130" ry="74" className="fill-brand-50" />
      <rect x="92" y="22" width="96" height="146" rx="10" strokeWidth="1.5" className="fill-surface stroke-brand-200" />
      <rect x="104" y="36" width="72" height="48" rx="6" className="fill-brand-50" />
      <rect x="104" y="94" width="72" height="60" rx="6" className="fill-brand-50" />
      <circle cx="174" cy="90" r="3.5" className="fill-brand-300" />
      <path d="M124 108 V94 a16 16 0 0 1 32 0 V108" fill="none" strokeWidth="7" strokeLinecap="round" className="stroke-brand-400" />
      <rect x="112" y="102" width="56" height="46" rx="10" className="fill-accent" />
      <circle cx="140" cy="120" r="6" className="fill-on-accent" />
      <rect x="137.5" y="122" width="5" height="13" rx="2.5" className="fill-on-accent" />
      <Sparkle x={58} y={46} size={8} />
      <Sparkle x={226} y={132} size={6} className="fill-brand-200" />
      <circle cx="222" cy="40" r="3" className="fill-brand-200" />
      <circle cx="50" cy="130" r="2.5" className="fill-brand-300" />
    </svg>
  )
}
