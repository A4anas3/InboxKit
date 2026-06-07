import React from 'react';
import {
  ArrowLeft,
  ChevronRight,
  Trophy,
  Layers,
  Edit2,
  Wifi,
  WifiOff,
  Target,
  Clock
} from 'lucide-react';
import CooldownRing from './CooldownRing';
import Leaderboard from './Leaderboard';

export default function Sidebar({
  connected,
  onlineCount,
  cooldownProgress,
  currentUser,
  rankings,
  lastActiveUserId,
  onClose,
  onLogout,
  onLogin
}) {
  const userEntry = rankings?.find(r => r.userId === currentUser?.userId);
  const userScore = userEntry?.score ?? 0;
  const userRank  = userEntry?.rank  ?? '—';
  const totalTiles = 9000; // 90×100

  return (
    <div className="w-full sm:w-[440px] h-screen bg-white flex flex-col overflow-y-auto shadow-2xl border-r border-slate-100">

      {/* ── HEADER BLOCK — exact copy of front's MobileMoreMenu header ── */}
      <div className="bg-[#e0f2fe] px-5 pt-6 pb-8 rounded-b-[40px] border-b border-sky-200/60 flex flex-col relative shrink-0">

        {/* Top Actions Row */}
        <div className="flex items-center justify-between w-full mb-6">
          {/* Left: ArrowLeft (only visible on mobile, hidden on full screen desktop) */}
          <button
            onClick={onClose}
            className="sm:hidden p-1.5 text-slate-800 hover:bg-slate-200/50 rounded-full transition-colors active:scale-95 cursor-pointer">
            <ArrowLeft size={22} />
          </button>

          <div className="flex items-center gap-3">
            {/* Help pill replaced by My Resume link */}
            <a
              href="https://drive.google.com/file/d/1I3b4xjSsu4zJNN9bKViQHaUtY8ccPoVE/view?usp=drive_link"
              target="_blank"
              rel="noopener noreferrer"
              className="px-4 py-1.5 border border-yellow-300 bg-yellow-400 text-slate-800 font-bold rounded-full text-xs shadow-sm hover:bg-yellow-500 active:scale-95 transition-all cursor-pointer inline-block text-center"
            >
              My Resume
            </a>
          </div>
        </div>

        {/* Profile Row — exact copy of front's profile section */}
        <div className="flex items-center gap-4 w-full text-left p-2 -mx-2 rounded-2xl">
          {/* Avatar: colored circle with first letter */}
          <div className="relative shrink-0">
            <div
              className="w-14 h-14 rounded-full text-white font-black text-lg flex items-center justify-center border-2 border-white shadow-md"
              style={{
                backgroundColor: currentUser?.color ?? '#4f46e5',
                boxShadow: `0 4px 14px ${currentUser?.color ?? '#4f46e5'}50`,
              }}
            >
              {(currentUser?.username ?? 'U').charAt(0).toUpperCase()}
            </div>
          </div>

          {/* Name + rank — exactly like front's Name + email */}
          <div className="text-left flex-1 min-w-0">
            <h2 className="text-xl sm:text-2xl font-black text-slate-800 tracking-tight leading-none mb-1.5 flex items-center gap-2">
              <span className="truncate">{currentUser?.username ?? 'Unknown'}</span>
            </h2>
            <p className="text-xs font-semibold text-slate-500 tracking-wide">
              Rank&nbsp;<span className="text-sky-600 font-bold">#{userRank}</span>
              &nbsp;·&nbsp;
              <span className="text-slate-600">{userScore} tiles claimed</span>
            </p>
          </div>
        </div>
      </div>

      {/* ── SCROLLABLE CONTENT ── */}
      <div className="flex-1 px-4 py-4 space-y-4 pb-16 bg-slate-50/50">

        {/* Network Status Card */}
        <div className="bg-white border border-slate-100 rounded-[24px] p-4 shadow-sm flex items-center justify-between">
          <div className="flex items-center gap-3">
            {connected
              ? <Wifi size={20} className="text-emerald-500 shrink-0" />
              : <WifiOff size={20} className="text-red-400 shrink-0" />
            }
            <div>
              <p className="text-xs font-black text-slate-700">
                {connected ? 'Connected' : 'Reconnecting...'}
              </p>
              <p className="text-[10px] text-slate-400 font-semibold">Tactical Network</p>
            </div>
          </div>
          <div className="text-right">
            <p className="text-lg font-black text-slate-800 leading-none">{onlineCount}</p>
            <p className="text-[10px] text-sky-600 font-bold uppercase tracking-wide">online</p>
          </div>
        </div>


        {/* Leaderboard Card */}
        <div className="bg-white border border-slate-100 rounded-[24px] p-4 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <Trophy size={15} className="text-amber-500 shrink-0" />
            <span className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Leaderboard</span>
          </div>
          <Leaderboard rankings={rankings} lastActiveUserId={lastActiveUserId} />
        </div>

        {/* Sign Out / Login Button */}
        {currentUser ? (
          <button
            onClick={onLogout}
            className="w-full bg-red-50 hover:bg-red-100 text-red-600 rounded-[20px] py-3 text-xs font-bold uppercase tracking-wider transition-all cursor-pointer shadow-sm hover:shadow-md active:scale-95"
          >
            Sign Out
          </button>
        ) : (
          <button
            onClick={onLogin}
            className="w-full bg-yellow-50 hover:bg-yellow-100 text-yellow-700 rounded-[20px] py-3 text-xs font-bold uppercase tracking-wider transition-all cursor-pointer shadow-sm hover:shadow-md active:scale-95"
          >
            Login
          </button>
        )}

        {/* Creativity Link styled as a button below signout */}
        <a
          href="https://ssbcoreai.in"
          target="_blank"
          rel="noopener noreferrer"
          className="w-full block text-center bg-sky-50 hover:bg-sky-100 text-sky-600 rounded-[20px] py-3 text-xs font-bold uppercase tracking-wider transition-all cursor-pointer shadow-sm hover:shadow-md active:scale-95 mt-3"
        >
          My Real Creativity in SSBCoreAi.in
        </a>

      </div>

      {/* ── FOOTER ── */}
      <div className="px-5 py-3 border-t border-slate-100 text-[10px] text-slate-400 text-center tracking-widest uppercase font-bold bg-white">
        GridWar v1.0.0
      </div>
    </div>
  );
}
