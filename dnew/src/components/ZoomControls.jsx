import React from 'react';
import { Plus, Minus, RotateCcw } from 'lucide-react';

export default function ZoomControls({ setViewport }) {
  const handleZoomIn = () => {
    setViewport((v) => ({ ...v, scale: Math.min(4.0, v.scale + 0.25) }));
  };

  const handleZoomOut = () => {
    setViewport((v) => ({ ...v, scale: Math.max(0.5, v.scale - 0.25) }));
  };

  const handleReset = () => {
    setViewport({ x: 0, y: 0, scale: 1.0 });
  };

  const btnClass =
    'bg-yellow-400 hover:bg-yellow-500 border border-yellow-300 text-slate-800 w-9 h-9 rounded-lg ' +
    'flex items-center justify-center cursor-pointer transition-all duration-200 ' +
    'shadow-md outline-none active:scale-95';

  return (
    <div className="flex gap-2">
      <button onClick={handleZoomIn} className={btnClass} title="Zoom In">
        <Plus size={18} className="pointer-events-none" />
      </button>
      <button onClick={handleZoomOut} className={btnClass} title="Zoom Out">
        <Minus size={18} className="pointer-events-none" />
      </button>
      <button onClick={handleReset} className={btnClass} title="Reset View">
        <RotateCcw size={16} className="pointer-events-none" />
      </button>
    </div>
  );
}
