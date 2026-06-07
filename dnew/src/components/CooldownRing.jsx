import React from 'react';

export default function CooldownRing({ progress }) {
  const RADIUS = 28;
  const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

  const isCooldownActive = progress > 0;
  const remainingSeconds = ((1 - progress) * 5).toFixed(1);

  // Light-theme colors: accent (saffron) when ready, red when cooling down
  const strokeColor = isCooldownActive ? '#ef4444' : 'hsl(35 80% 55%)';
  const trackColor = 'hsl(220 14% 92%)'; // --color-muted

  return (
    <div className="flex flex-col items-center gap-1.5">
      <svg width="70" height="70">
        {/* Track circle */}
        <circle
          cx="35" cy="35" r={RADIUS}
          stroke={trackColor}
          strokeWidth="4"
          fill="none"
        />
        {/* Progress arc */}
        <circle
          cx="35" cy="35" r={RADIUS}
          stroke={strokeColor}
          strokeWidth="4"
          fill="none"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={CIRCUMFERENCE * (1 - progress)}
          strokeLinecap="round"
          transform="rotate(-90 35 35)"
          style={{ transition: 'stroke 0.3s, stroke-dashoffset 0.15s linear' }}
        />
        {/* Center label */}
        <text
          x="35" y="40"
          textAnchor="middle"
          fill="hsl(220 70% 15%)"
          fontSize="10"
          fontFamily="Inter, sans-serif"
          fontWeight="700"
        >
          {isCooldownActive ? `${remainingSeconds}s` : 'READY'}
        </text>
      </svg>
      <span
        className="text-[0.6rem] uppercase tracking-widest font-semibold"
        style={{ color: isCooldownActive ? '#ef4444' : 'hsl(35 80% 55%)' }}
      >
        {isCooldownActive ? 'Cooldown' : 'Grid Ready'}
      </span>
    </div>
  );
}
