import React from 'react';

export default function ConnectionStatus({ connected }) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={`w-2 h-2 rounded-full inline-block shrink-0 ${connected ? '' : 'pulse-dot'}`}
        style={{
          backgroundColor: connected ? '#22c55e' : '#ef4444',
          boxShadow: connected
            ? '0 0 6px rgba(34, 197, 94, 0.55)'
            : '0 0 6px rgba(239, 68, 68, 0.55)',
          transition: 'background-color 0.3s, box-shadow 0.3s',
        }}
      />
      <span className="text-[0.7rem] font-semibold uppercase tracking-widest text-muted-foreground">
        {connected ? 'Connected' : 'Reconnecting'}
      </span>
    </div>
  );
}
