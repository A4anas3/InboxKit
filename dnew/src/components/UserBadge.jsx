import React from 'react';

export default function UserBadge({ user }) {
  if (!user) return null;

  return (
    <div className="flex items-center gap-3 bg-secondary border border-border rounded-xl p-3 px-4 w-full">
      {/* Color dot matching user's territory color */}
      <div
        className="w-7 h-7 rounded-lg border border-border shrink-0"
        style={{
          backgroundColor: user.color,
          boxShadow: `0 0 8px ${user.color}50`,
        }}
      />
      <div className="overflow-hidden">
        <div className="text-[0.6rem] text-muted-foreground uppercase tracking-widest font-semibold mb-0.5">
          Player
        </div>
        <div className="text-sm text-primary font-bold overflow-hidden text-ellipsis whitespace-nowrap">
          {user.username}
        </div>
      </div>
    </div>
  );
}
