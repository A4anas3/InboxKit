import React, { useEffect, useState } from 'react';
import { ChevronRight } from 'lucide-react';

export default function Leaderboard({ rankings, lastActiveUserId }) {
  const [highlightedUser, setHighlightedUser] = useState(null);

  useEffect(() => {
    if (lastActiveUserId) {
      setHighlightedUser(lastActiveUserId);
      const timer = setTimeout(() => setHighlightedUser(null), 1500);
      return () => clearTimeout(timer);
    }
  }, [lastActiveUserId]);

  if (!rankings || rankings.length === 0) {
    return (
      <p className="text-center text-xs text-slate-400 py-4 italic">
        No active claims yet.
      </p>
    );
  }

  return (
    /* Exact same list container style as front MobileMoreMenu */
    <div className="bg-white border border-slate-100/80 rounded-[20px] overflow-hidden flex flex-col">
      {rankings.map((entry, idx) => {
        const isHighlighted = entry.userId === highlightedUser;
        const isLast = idx === rankings.length - 1;

        return (
          <div
            key={entry.userId}
            className={`w-full py-3 px-4 flex items-center justify-between transition-all duration-200 ${
              isLast ? '' : 'border-b border-slate-100/80'
            } ${isHighlighted ? 'bg-orange-50/60' : 'bg-white hover:bg-slate-50/50'}`}
          >
            <div className="flex items-center gap-3">
              {/* Rank number */}
              <span className="text-xs font-black text-slate-400 w-5 text-right shrink-0">
                #{entry.rank ?? idx + 1}
              </span>

              {/* User color dot */}
              <span
                className="w-2.5 h-2.5 rounded-full shrink-0"
                style={{
                  backgroundColor: entry.color || '#888',
                  boxShadow: `0 0 6px ${entry.color || '#888'}70`,
                }}
              />

              {/* Username */}
              <span className={`text-sm font-bold truncate max-w-[110px] ${
                isHighlighted ? 'text-orange-600' : 'text-slate-700'
              }`}>
                {entry.username}
              </span>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <span className={`text-sm font-black ${
                isHighlighted ? 'text-orange-500' : 'text-slate-500'
              }`}>
                {entry.score}
              </span>
              <ChevronRight size={14} className="text-slate-300" />
            </div>
          </div>
        );
      })}
    </div>
  );
}
