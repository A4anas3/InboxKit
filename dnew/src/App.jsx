import React, { useState, useEffect } from 'react';
import AuthOverlay from './components/AuthOverlay';
import Sidebar from './components/Sidebar';
import GridCanvas from './components/GridCanvas';
import ZoomControls from './components/ZoomControls';
import Tooltip from './components/Tooltip';
import { api } from './lib/api';
import { Menu, Wifi, WifiOff } from 'lucide-react';
import { supabase } from './lib/supabaseClient';

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [connected, setConnected] = useState(false);
  const [showAuthModal, setShowAuthModal] = useState(false);
  const [cooldownProgress, setCooldownProgress] = useState(0);
  const [leaderboard, setLeaderboard] = useState([]);
  const [tooltip, setTooltip] = useState(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [onlineCount, setOnlineCount] = useState(0);
  const [lastActiveUserId, setLastActiveUserId] = useState(null);

  // Zoom/pan viewport state - only for zoom/pan CSS transform
  const [viewport, setViewport] = useState({ x: 0, y: 0, scale: 1.0 });

  // 1. Session restoration on load and auth state subscription
  useEffect(() => {
    const restoreSession = async () => {
      try {
        const { data: { session } } = await supabase.auth.getSession();
        if (session) {
          const token = session.access_token;
          const metaUsername = session.user?.user_metadata?.username;
          const finalUsername = metaUsername || session.user?.email?.split('@')[0] || 'Player';

          const response = await api.post('/api/users/join', {
            username: finalUsername
          }, {
            Authorization: `Bearer ${token}`
          });
          if (response && response.userId) {
            localStorage.setItem('gridwar_user', JSON.stringify({ ...response, token }));
            setCurrentUser({ ...response, token });
          }
        } else {
          localStorage.removeItem('gridwar_user');
          setCurrentUser(null);
        }
      } catch (e) {
        console.error('Failed to restore auth session:', e);
        const storedUser = localStorage.getItem('gridwar_user');
        if (storedUser) {
          try {
            setCurrentUser(JSON.parse(storedUser));
          } catch (err) {
            localStorage.removeItem('gridwar_user');
          }
        }
      }
    };

    restoreSession();

    const { data: { subscription } } = supabase.auth.onAuthStateChange(async (event, session) => {
      if (event === 'SIGNED_IN' && session) {
        const token = session.access_token;
        const metaUsername = session.user?.user_metadata?.username;
        const finalUsername = metaUsername || session.user?.email?.split('@')[0] || 'Player';
        try {
          const response = await api.post('/api/users/join', {
            username: finalUsername
          }, {
            Authorization: `Bearer ${token}`
          });
          if (response && response.userId) {
            localStorage.setItem('gridwar_user', JSON.stringify({ ...response, token }));
            setCurrentUser({ ...response, token });
            setShowAuthModal(false);
          }
        } catch (e) {
          console.error('Error syncing auth state change:', e);
        }
      } else if (event === 'SIGNED_OUT') {
        localStorage.removeItem('gridwar_user');
        setCurrentUser(null);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, []);

  // 2. HTTP Polling for Online Count (every 15s) and Leaderboard fallback (every 10s)
  useEffect(() => {
    const fetchOnlineCount = async () => {
      try {
        const data = await api.get('/api/online-count');
        if (data && typeof data.count === 'number') {
          setOnlineCount(data.count);
        }
      } catch (err) {
        console.error('Error fetching online count:', err);
      }
    };

    const fetchLeaderboard = async () => {
      try {
        const data = await api.get('/api/leaderboard');
        if (data && data.rankings) {
          setLeaderboard(data.rankings);
        }
      } catch (err) {
        console.error('Error fetching leaderboard:', err);
      }
    };

    fetchOnlineCount();
    fetchLeaderboard();

    const onlineInterval = setInterval(fetchOnlineCount, 15000);
    const leaderboardInterval = setInterval(fetchLeaderboard, 10000);

    return () => {
      clearInterval(onlineInterval);
      clearInterval(leaderboardInterval);
    };
  }, []);

  const handleLogout = async () => {
    try {
      await supabase.auth.signOut();
    } catch (e) {
      console.error('Failed to sign out from Supabase:', e);
    }
    localStorage.removeItem('gridwar_user');
    setCurrentUser(null);
  };

  return (
    <div style={{ width: '100vw', height: '100vh', display: 'flex', overflow: 'hidden', backgroundColor: '#e0f2fe' }}>
      
      {/* ── SIDEBAR (mobile: slide-in overlay, desktop: fixed left) ── */}
      {/* Mobile backdrop */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-[9998] bg-black/40 backdrop-blur-sm"
          onClick={() => setSidebarOpen(false)}
        />
      )}
      <div
        className={`fixed sm:relative inset-y-0 left-0 z-[9999] sm:z-auto transition-transform duration-300 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full sm:translate-x-0'
        }`}
      >
        <Sidebar
          connected={connected}
          onlineCount={onlineCount}
          cooldownProgress={cooldownProgress}
          currentUser={currentUser}
          rankings={leaderboard}
          lastActiveUserId={lastActiveUserId}
          onClose={() => setSidebarOpen(false)}
          onLogout={handleLogout}
          onLogin={() => setShowAuthModal(true)}
        />
      </div>

      {/* ── MAIN AREA ── */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>

        {/* ── TOP HEADER — exact front project style ── */}
        <div
          style={{
            background: '#ffffff',
            borderBottom: '1px solid #bae6fd',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 20px',
            height: '56px',
            shrink: 0,
            boxShadow: '0 1px 3px rgba(0, 0, 0, 0.05)',
            zIndex: 10,
          }}
        >
          {/* Left: hamburger (mobile) + title */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <button
              onClick={() => setSidebarOpen(true)}
              className="sm:hidden p-1.5 text-slate-800 hover:bg-sky-100 rounded-full transition-colors"
            >
              <Menu size={22} />
            </button>
            <span style={{ fontFamily: '"Playfair Display", serif', fontWeight: 900, fontSize: '1.3rem', color: '#1e293b', letterSpacing: '-0.5px' }}>
              Grid<span style={{ color: 'hsl(35 80% 50%)' }}>War</span>
            </span>
          </div>

          {/* Right: connection status + online count pill */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            {/* Connection pill */}
            <div style={{
              display: 'flex', alignItems: 'center', gap: '6px',
              padding: '6px 14px', borderRadius: '9999px',
              border: '1px solid rgba(251,146,60,0.3)',
              background: '#fff',
              fontSize: '12px', fontWeight: 700,
              color: connected ? '#16a34a' : '#dc2626',
            }}>
              {connected
                ? <Wifi size={14} />
                : <WifiOff size={14} />
              }
              {connected ? 'Live' : 'Offline'}
            </div>

            {/* Online count pill — mirrors front's AiQuotaTracker "0/30" */}
            <div style={{
              display: 'flex', alignItems: 'center', gap: '6px',
              padding: '6px 14px', borderRadius: '9999px',
              border: '1px solid rgba(251,146,60,0.35)',
              background: '#fff',
              fontSize: '12px', fontWeight: 700,
              color: '#ea580c',
            }}>
              <span>👥</span>
              <span>{onlineCount} online</span>
            </div>

            {/* Sign In Button for spectators */}
            {!currentUser && (
              <button
                onClick={() => setShowAuthModal(true)}
                className="bg-yellow-400 hover:bg-yellow-500 text-slate-800 font-bold px-4 py-1.5 rounded-full text-xs shadow-sm active:scale-95 transition-all cursor-pointer"
              >
                Sign In
              </button>
            )}
          </div>
        </div>

        {/* ── GRID CANVAS fills remaining height ── */}
        <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
          <GridCanvas
            currentUser={currentUser}
            setConnected={setConnected}
            setLeaderboard={setLeaderboard}
            setCooldownProgress={setCooldownProgress}
            setTooltip={setTooltip}
            onTileActiveClaimed={(userId) => {
              setLastActiveUserId(userId);
            }}
            viewport={viewport}
            setViewport={setViewport}
            onAuthRequired={() => setShowAuthModal(true)}
          />

          {/* Zoom controls */}
          <div style={{ position: 'absolute', bottom: '24px', right: '24px', zIndex: 10 }}>
            <ZoomControls setViewport={setViewport} />
          </div>

          <Tooltip tooltip={tooltip} />
        </div>
      </div>

      {/* Auth overlay modal */}
      {showAuthModal && (
        <AuthOverlay
          onClose={() => setShowAuthModal(false)}
          onAuthComplete={(user) => {
            setCurrentUser(user);
            setShowAuthModal(false);
          }}
        />
      )}
    </div>
  );
}
