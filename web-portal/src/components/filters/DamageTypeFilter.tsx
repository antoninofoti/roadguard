/**
 * Damage type checkbox filter.
 */

import { DAMAGE_TYPES, formatDamageType } from '../../types/report';

interface DamageTypeFilterProps {
  selected: string[];
  onChange: (types: string[]) => void;
}

export function DamageTypeFilter({ selected, onChange }: DamageTypeFilterProps) {
  const toggle = (type: string) => {
    if (selected.includes(type)) {
      onChange(selected.filter((t) => t !== type));
    } else {
      onChange([...selected, type]);
    }
  };

  const typeIcons: Record<string, string> = {
    pothole: '🕳️',
    bump: '⬆️',
    speed_bump: '🔶',
    roughness: '〰️',
  };

  return (
    <div>
      <label className="text-xs font-medium text-slate-400 uppercase tracking-wider mb-2 block">
        Damage Type
      </label>
      <div className="space-y-1">
        {DAMAGE_TYPES.map((type) => {
          const isActive = selected.includes(type);

          return (
            <button
              key={type}
              onClick={() => toggle(type)}
              className={`
                flex items-center gap-2 w-full px-3 py-2 rounded-lg text-xs font-medium
                transition-all duration-200
                ${
                  isActive
                    ? 'bg-[var(--color-accent-primary)]/10 text-[var(--color-accent-primary)] border border-[var(--color-accent-primary)]/30'
                    : 'text-slate-400 hover:text-slate-300 hover:bg-[var(--color-surface-700)] border border-transparent'
                }
              `}
            >
              <span>{typeIcons[type] ?? '📍'}</span>
              <span>{formatDamageType(type)}</span>
            </button>
          );
        })}
      </div>

      {selected.length > 0 && (
        <button
          onClick={() => onChange([])}
          className="mt-2 text-xs text-slate-500 hover:text-slate-300 transition-colors"
        >
          Clear filter
        </button>
      )}
    </div>
  );
}
