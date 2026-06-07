# 🎨 GridWar Frontend: Vite + React Canvas Client

This directory houses the client dashboard interface for **GridWar**, a real-time multiplayer grid claiming game.

For a comprehensive explanation of the entire system architecture, Spring Boot backend details, and deployment guides, please refer to the main [Root README.md](../README.md).

---

## 🏗️ Frontend Component Structure

*   `src`
    *   `main.jsx`: Mounts the React application.
    *   `App.jsx`: Root component containing layout composition, authentication session restoration (Supabase), and state management (online users count, leaderboard, and theme settings).
    *   `App.css` & `index.css`: Tailwind utility style extensions, custom animations, variables, and typography parameters.
    *   `lib`
        *   `api.js`: Wrapper utility for standard REST calls.
        *   `supabaseClient.js`: Initialized Supabase client instance configured to read key configurations from `.env.development` or `.env.production`.
    *   `components`
        *   `GridCanvas.jsx`: **Core Graphics Component**. Controls the 1699×1529 HTML5 canvas, mouse/drag panning gestures, zoom multipliers, optimistic UI updates, and incoming STOMP updates via a dedicated WebSocket connection.
        *   `MiniMap.jsx`: Visual viewport outline overlays.
        *   `Sidebar.jsx`: Slide-out component detailing connection statuses, leaderboard tables, active user details, and controls.
        *   `AuthOverlay.jsx`: Pop-up window for sign-in / registration integrations using Supabase auth forms.
        *   `ZoomControls.jsx`: Zoom overlay controls (`+`, `-`, `Reset`).
        *   `Tooltip.jsx`: Interactive tooltips displaying owners, hex colors, claimed dates, and coordinates.

---

## 🚀 Key Client Engine Systems

### 1. HTML5 Canvas Graphics Loop
Instead of building heavy React DOM matrices that would degrade frame rates, GridWar uses a single `<canvas>` element drawn via 2D Context context buffers.
*   **Drawing Methods**:
    *   `drawTile`: Sets fill/border paths for a single tile coordinate.
    *   `drawFullGrid`: Full refresh drawing coordinates. Called only once during WebSocket connect handshakes.
    *   `pulseNewTile`: Animates claims by running a fade/shrink cycle (`requestAnimationFrame`) centered on the target coordinate.
    *   `flashTileError`: Temporarily renders a translucent red mask over coordinates during rejection states.
*   **Performance Optimization (Batching)**:
    Updates are stored inside a mutable queue (`pendingDraws.current`) and painted inside a single `requestAnimationFrame` callback.

### 2. Viewport Navigation
*   **Pan & Drag**: Uses cursor client coordinate offsets to determine absolute translations. Panning translates CSS matrix coordinates of the canvas wrapping wrapper (`translate(x, y)`).
*   **Zoom Matrix**: Modulates scales (`scale(n)`) using scroll wheel listeners, maintaining viewport center calculations.

---

## ⚡ Setup & Run

1. Verify backend instance hostnames inside `.env.development`.
2. Run installation and launch commands:
   ```bash
   npm install
   npm run dev
   ```
