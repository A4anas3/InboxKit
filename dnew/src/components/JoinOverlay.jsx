import React, { useState } from 'react';
import { api } from '../lib/api';

export default function JoinOverlay({ onJoinComplete }) {
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [isSlidingUp, setIsSlidingUp] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username.trim()) {
      setError('Username cannot be empty');
      return;
    }
    setError('');
    setLoading(true);

    try {
      const response = await api.post('/api/users/join', { username: username.trim() });
      if (response && response.userId) {
        localStorage.setItem('gridwar_user', JSON.stringify(response));
        setIsSlidingUp(true);
        setTimeout(() => {
          onJoinComplete(response);
        }, 600);
      } else {
        setError('Invalid response from server');
      }
    } catch (err) {
      console.error(err);
      setError('Failed to join the grid. Server might be offline.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-[9999] flex items-center justify-center"
      style={{
        background: 'linear-gradient(135deg, hsl(220 14% 96%) 0%, hsl(200 60% 94%) 50%, hsl(220 14% 96%) 100%)',
        backgroundSize: '400% 400%',
        transition: 'transform 0.6s cubic-bezier(0.85, 0, 0.15, 1), opacity 0.6s ease',
        transform: isSlidingUp ? 'translateY(-100%)' : 'translateY(0)',
        opacity: isSlidingUp ? 0 : 1,
      }}
    >
      {/* Card */}
      <div className="w-[90%] max-w-[400px] bg-card border border-border shadow-2xl rounded-2xl overflow-hidden">

        {/* Card header — cream bg like front profile section */}
        <div
          className="px-8 py-8 text-center"
          style={{ background: 'hsl(35 60% 97%)' }}
        >
          {/* Logo mark */}
          <div className="w-14 h-14 rounded-2xl bg-primary mx-auto flex items-center justify-center mb-4 shadow-lg">
            <span className="text-white font-display font-bold text-2xl">G</span>
          </div>
          <h1 className="font-display font-bold text-3xl text-primary tracking-tight mb-1">
            Grid<span className="text-accent">War</span>
          </h1>
          <p className="text-muted-foreground text-sm">
            Territory Control · Claim your tiles
          </p>
        </div>

        {/* Form body */}
        <div className="px-8 py-6 bg-card">
          <form onSubmit={handleSubmit}>
            <div className="mb-4 text-left">
              <label
                htmlFor="username-input"
                className="block text-xs text-muted-foreground uppercase tracking-widest font-semibold mb-2"
              >
                Choose your name
              </label>
              <input
                id="username-input"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="e.g. Commander_Anas"
                maxLength={20}
                disabled={loading}
                className="w-full bg-background border border-border rounded-lg px-4 py-3 text-foreground text-sm font-sans outline-none transition-all duration-200 focus:border-primary focus:ring-2 focus:ring-primary/10 placeholder:text-muted-foreground/60"
              />
            </div>

            {error && (
              <p className="text-red-500 text-xs mb-4 font-medium flex items-center gap-1.5">
                <span>⚠️</span> {error}
              </p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-yellow-400 hover:bg-yellow-500 text-slate-800 rounded-lg py-3 text-sm font-bold tracking-wide transition-all duration-200 shadow-md hover:-translate-y-0.5 hover:shadow-lg disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {loading ? 'Joining...' : 'Join the Grid →'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
