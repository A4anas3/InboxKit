import React from 'react';

export default function Tooltip({ tooltip }) {
  if (!tooltip) return null;

  return (
    <div
      className="fixed bg-slate-900/95 backdrop-blur-sm rounded-lg p-2.5 px-3.5 pointer-events-none font-sans text-xs text-white z-[1000] border border-slate-700/50 shadow-[0_8px_30px_rgb(0,0,0,0.5)] [animation:tooltipFade_0.15s_cubic-bezier(0.16,1,0.3,1)_forwards]"
      style={{
        left: tooltip.x,
        top: tooltip.y,
        boxShadow: `0 8px 30px rgba(0, 0, 0, 0.5), 0 0 10px ${(tooltip.color || '#fff')}22`,
      }}
    >
      <style>
        {`
          @keyframes tooltipFade {
            from { opacity: 0; transform: translateY(4px); }
            to { opacity: 1; transform: translateY(0); }
          }
        `}
      </style>
      <div className="flex items-center gap-1.5 mb-1">
        <span style={{ color: tooltip.color }} className="text-sm">■</span>
        <span className="font-extrabold text-slate-100">{tooltip.owner || 'Unknown'}</span>
      </div>
      <div className="text-slate-400 text-[9px] uppercase tracking-[0.5px]">
        Claimed at {new Date(tooltip.claimedAt).toLocaleTimeString()}
      </div>
    </div>
  );
}
