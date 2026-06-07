import React, { useState } from 'react';
import { supabase } from '../lib/supabaseClient';
import { X } from 'lucide-react';

export default function AuthOverlay({ onClose }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleGoogleLogin = async () => {
    setError('');
    setLoading(true);
    try {
      const { error } = await supabase.auth.signInWithOAuth({
        provider: 'google',
        options: {
          redirectTo: window.location.origin
        }
      });
      if (error) throw error;
    } catch (err) {
      console.error(err);
      setError(err.message || 'Failed to initiate Google login');
      setLoading(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/40 backdrop-blur-sm px-4"
      onClick={onClose}
    >
      <div
        className="w-full max-w-[380px] bg-white border border-slate-100 shadow-2xl rounded-3xl overflow-hidden relative"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-slate-600 rounded-full transition-colors active:scale-95 cursor-pointer z-10"
        >
          <X size={20} />
        </button>

        {/* Header Section */}
        <div className="bg-[#e0f2fe] px-8 py-8 text-center border-b border-sky-100">
          <div className="w-12 h-12 rounded-2xl bg-yellow-400 mx-auto flex items-center justify-center mb-3 shadow-md">
            <span className="text-slate-800 font-display font-extrabold text-xl">G</span>
          </div>
          <h2 className="font-display font-black text-2xl text-slate-800 tracking-tight leading-none mb-1">
            Grid<span className="text-sky-600">War</span>
          </h2>
          <p className="text-slate-500 text-xs font-semibold uppercase tracking-wider">
            Authentication Required
          </p>
        </div>

        {/* Content Section */}
        <div className="p-8 text-center space-y-6">
          <p className="text-slate-600 text-sm font-semibold leading-relaxed">
            To claim tiles and participate in the live leaderboard, please authenticate with your account.
          </p>

          {error && (
            <p className="text-red-500 text-xs font-semibold leading-relaxed">
              ⚠️ {error}
            </p>
          )}

          {/* Google Button */}
          <button
            type="button"
            onClick={handleGoogleLogin}
            disabled={loading}
            className="w-full bg-yellow-400 hover:bg-yellow-500 text-slate-800 rounded-xl py-3.5 text-sm font-bold flex items-center justify-center gap-3 shadow-md transition-all active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed cursor-pointer"
          >
            <svg className="h-5 w-5 shrink-0" viewBox="0 0 24 24">
              <path
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                fill="#4285F4"
              />
              <path
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                fill="#34A853"
              />
              <path
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
                fill="#FBBC05"
              />
              <path
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
                fill="#EA4335"
              />
            </svg>
            {loading ? 'Connecting...' : 'Continue with Google'}
          </button>
        </div>
      </div>
    </div>
  );
}
