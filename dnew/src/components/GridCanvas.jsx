import React, { useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_BASE_URL, api } from '../lib/api';

const TILE_SIZE = 16;
const GAP = 1;
const STEP = TILE_SIZE + GAP;
const GRID_ROWS = 90;
const GRID_COLS = 100;
const CANVAS_PIXEL_W = GRID_COLS * STEP - GAP; // 100 * 17 - 1 = 1699px
const CANVAS_PIXEL_H = GRID_ROWS * STEP - GAP; // 90  * 17 - 1 = 1529px

export default function GridCanvas({
  currentUser,
  setConnected,
  setLeaderboard,
  setCooldownProgress,
  setTooltip,
  onTileActiveClaimed,
  viewport,
  setViewport,
  onAuthRequired
}) {
  const canvasRef = useRef(null);
  const miniMapRef = useRef(null);
  const containerRef = useRef(null);

  // Core mutable references (spec requirements)
  const gridState = useRef({}); // key: "row_col", value: { userId, username, color, claimedAt }
  const ctx = useRef(null);
  const pendingDraws = useRef([]);
  const pendingClaims = useRef({});
  const cooldownActive = useRef(false);
  const stompClient = useRef(null);

  // Other internal mutable references
  const heartbeatInterval = useRef(null);
  const lastClickedTileId = useRef(null);
  const lastHoveredTile = useRef(null);
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0 });
  const rafScheduled = useRef(false);
  const isMouseDown = useRef(false);
  const mouseDownPos = useRef({ x: 0, y: 0 });
  const wasDragging = useRef(false);

  // ── Canvas Drawing Functions (strictly adhering to Rule 3 & 4) ────────────────

  function drawTile(context, row, col, tileData, highlight = false) {
    if (!context) return;
    const x = col * STEP;
    const y = row * STEP;

    // Background
    context.fillStyle = tileData ? tileData.color : '#e2e8f0';
    context.fillRect(x, y, TILE_SIZE, TILE_SIZE);

    // Border
    context.strokeStyle = highlight ? '#f97316' : '#cbd5e1';
    context.lineWidth = highlight ? 2 : 0.5;
    context.strokeRect(x + 0.5, y + 0.5, TILE_SIZE - 1, TILE_SIZE - 1);
  }

  function drawFullGrid(context, state) {
    if (!context) return;
    context.clearRect(0, 0, CANVAS_PIXEL_W, CANVAS_PIXEL_H);
    for (let row = 0; row < GRID_ROWS; row++) {
      for (let col = 0; col < GRID_COLS; col++) {
        const tileData = state[`${row}_${col}`];
        drawTile(context, row, col, tileData);
      }
    }
  }

  // Redraws the 3x3 neighborhood around a tile to clear pulse ring artifacts
  function redrawNeighborhood(context, centerRow, centerCol) {
    for (let r = centerRow - 1; r <= centerRow + 1; r++) {
      for (let c = centerCol - 1; c <= centerCol + 1; c++) {
        if (r >= 0 && r < GRID_ROWS && c >= 0 && c < GRID_COLS) {
          const tileData = gridState.current[`${r}_${c}`];
          drawTile(context, r, c, tileData, false);
        }
      }
    }
  }

  function pulseNewTile(context, row, col, tileData) {
    if (!context) return;
    let opacity = 1.0;
    let size = 1.4; // start larger
    
    const animate = () => {
      if (size <= 1.0) {
        // Redraw neighborhood one final time to clean up
        redrawNeighborhood(context, row, col);
        drawTile(context, row, col, tileData, false); // final steady state
        return;
      }

      // Redraw neighborhood to clean previous frame's ring
      redrawNeighborhood(context, row, col);

      // Draw expanding glow ring
      context.save();
      context.globalAlpha = opacity;
      context.strokeStyle = tileData.color;
      context.lineWidth = 2;
      context.strokeRect(
        col * STEP - (size - 1) * TILE_SIZE / 2,
        row * STEP - (size - 1) * TILE_SIZE / 2,
        TILE_SIZE * size,
        TILE_SIZE * size
      );
      context.restore();

      // Keep the center tile drawn solid on top
      drawTile(context, row, col, tileData, false);

      size -= 0.05;
      opacity -= 0.08;
      requestAnimationFrame(animate);
    };
    animate();
  }

  function flashTileError(context, row, col) {
    if (!context) return;
    const x = col * STEP;
    const y = row * STEP;
    context.fillStyle = '#ff3d3d88'; // Error flash color
    context.fillRect(x, y, TILE_SIZE, TILE_SIZE);
    setTimeout(() => {
      const tileData = gridState.current[`${row}_${col}`];
      drawTile(context, row, col, tileData);
    }, 400);
  }

  // ── rAF Batching ─────────────────────────────────────────────────────────────

  function scheduleFlush() {
    if (rafScheduled.current) return;
    rafScheduled.current = true;
    requestAnimationFrame(flushDraws);
  }

  function flushDraws() {
    const batch = pendingDraws.current.splice(0);
    for (const { row, col, tileData } of batch) {
      drawTile(ctx.current, row, col, tileData);
      pulseNewTile(ctx.current, row, col, tileData);
    }
    rafScheduled.current = false;
    updateMiniMap();
  }

  // ── Mini Map Drawing ──────────────────────────────────────────────────────────

  function updateMiniMap() {
    const miniCanvas = miniMapRef.current;
    if (!miniCanvas) return;
    const miniCtx = miniCanvas.getContext('2d');
    if (!miniCtx) return;

    miniCtx.clearRect(0, 0, 50, 50);
    // 50px mini-canvas divided by cols/rows to get per-tile pixel size
    const miniTileW = 50 / GRID_COLS;
    const miniTileH = 50 / GRID_ROWS;

    // Draw tiny representation of all grid tiles
    for (let row = 0; row < GRID_ROWS; row++) {
      for (let col = 0; col < GRID_COLS; col++) {
        const tileData = gridState.current[`${row}_${col}`];
        miniCtx.fillStyle = tileData ? tileData.color : '#e2e8f0';
        miniCtx.fillRect(col * miniTileW, row * miniTileH, miniTileW, miniTileH);
      }
    }

    // Draw active viewport outline
    if (containerRef.current) {
      const mainWidth = containerRef.current.clientWidth;
      const mainHeight = containerRef.current.clientHeight;

      const viewLeft = -viewport.x / viewport.scale;
      const viewTop  = -viewport.y / viewport.scale;
      const viewWidth  = mainWidth  / viewport.scale;
      const viewHeight = mainHeight / viewport.scale;

      const scaleX = 50 / CANVAS_PIXEL_W;
      const scaleY = 50 / CANVAS_PIXEL_H;
      const miniLeft   = Math.max(0, Math.min(50, viewLeft   * scaleX));
      const miniTop    = Math.max(0, Math.min(50, viewTop    * scaleY));
      const miniWidth  = Math.max(2, Math.min(50, viewWidth  * scaleX));
      const miniHeight = Math.max(2, Math.min(50, viewHeight * scaleY));

      miniCtx.strokeStyle = '#f97316';
      miniCtx.lineWidth = 1;
      miniCtx.strokeRect(miniLeft, miniTop, miniWidth, miniHeight);
    }
  }

  // Update minimap when viewport changes
  useEffect(() => {
    updateMiniMap();
  }, [viewport]);

  // Attach wheel listener as non-passive so preventDefault() works (prevents page scroll on zoom)
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, [viewport]);

  // ── Cooldown Timer ──────────────────────────────────────────────────────────

  function startCooldownTimer() {
    cooldownActive.current = true;
    const duration = 5000;
    const start = Date.now();

    const tick = () => {
      const elapsed = Date.now() - start;
      const progress = Math.min(1, elapsed / duration);
      setCooldownProgress(progress);

      if (progress < 1) {
        requestAnimationFrame(tick);
      } else {
        cooldownActive.current = false;
        setCooldownProgress(0);
      }
    };
    requestAnimationFrame(tick);
  }

  // ── API and Connection Management ───────────────────────────────────────────

  function fetchFullGrid() {
    api.get('/api/grid')
      .then(data => {
        gridState.current = data.tiles || {};
        drawFullGrid(ctx.current, gridState.current);
        updateMiniMap();
      })
      .catch(err => {
        console.error('Error fetching full grid state:', err);
      });
  }

  function handleServerError(error) {
    if (error.type === 'COOLDOWN') {
      return;
    }

    if (error.type === 'ALREADY_OWNED') {
      // Revert the optimistic draw on conflict
      const tileId = error.tileId || lastClickedTileId.current;
      if (!tileId) return;

      const [row, col] = tileId.split('_').map(Number);
      const actual = error.currentOwner || null;
      gridState.current[tileId] = actual;
      drawTile(ctx.current, row, col, actual);

      // Flash red on that tile briefly
      flashTileError(ctx.current, row, col);
    }
  }

  function onGridUpdate(update) {
    const { tileId, ...tileData } = update;
    const [row, col] = tileId.split('_').map(Number);

    // Clear revert timeout if this was our optimistic claim
    if (pendingClaims.current[tileId]) {
      clearTimeout(pendingClaims.current[tileId]);
      delete pendingClaims.current[tileId];
    }

    // null userId means the tile was decolored (freed)
    const resolvedData = tileData.userId ? tileData : null;

    // Update state ref
    gridState.current[tileId] = resolvedData;

    // Queue draw for next animation frame
    pendingDraws.current.push({ row, col, tileData: resolvedData });
    scheduleFlush();

    if (resolvedData) {
      onTileActiveClaimed(resolvedData.userId);
    }
  }

  function connect() {
    const token = currentUser?.token;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE_URL}/ws`),
      connectHeaders: token ? {
        Authorization: `Bearer ${token}`
      } : {},
      reconnectDelay: 500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      onConnect: () => {
        setConnected(true);

        // Subscribe to tile updates
        client.subscribe('/topic/grid', (msg) => {
          const update = JSON.parse(msg.body);
          onGridUpdate(update);
        });

        // Subscribe to leaderboard
        client.subscribe('/topic/leaderboard', (msg) => {
          const data = JSON.parse(msg.body);
          if (data && data.rankings) {
            setLeaderboard(data.rankings);
          }
        });

        // Subscribe to personal errors
        client.subscribe('/user/queue/error', (msg) => {
          const error = JSON.parse(msg.body);
          handleServerError(error);
        });

        // Load full grid via HTTP after connect
        fetchFullGrid();

        // Send heartbeat immediately on connect to refresh online TTL
        const sendHeartbeat = () => {
          if (client.connected && currentUser) {
            client.publish({
              destination: '/app/heartbeat',
              body: JSON.stringify({ userId: currentUser.userId })
            });
          }
        };
        sendHeartbeat();
        heartbeatInterval.current = setInterval(sendHeartbeat, 15000);
      },

      onDisconnect: () => {
        setConnected(false);
        if (heartbeatInterval.current) {
          clearInterval(heartbeatInterval.current);
        }
      },

      onStompError: (frame) => {
        console.error('STOMP error', frame);
        setConnected(false);
      }
    });

    client.activate();
    stompClient.current = client;
  }

  useEffect(() => {
    // Canvas context initialization
    if (canvasRef.current) {
      ctx.current = canvasRef.current.getContext('2d');
      drawFullGrid(ctx.current, gridState.current);
      updateMiniMap();
    }

    // Connect WebSocket
    connect();

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
      if (heartbeatInterval.current) {
        clearInterval(heartbeatInterval.current);
      }
    };
  }, [currentUser]);

  // ── Coordinates and Input Handlers ─────────────────────────────────────────

  function getTileFromMouseEvent(e) {
    const rect = canvasRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / viewport.scale;
    const y = (e.clientY - rect.top) / viewport.scale;
    const col = Math.floor(x / STEP);
    const row = Math.floor(y / STEP);
    return { row, col };
  }

  function handleCanvasClick(e) {
    if (wasDragging.current) {
      wasDragging.current = false;
      return;
    }
    if (!currentUser) {
      onAuthRequired();
      return;
    }
    const { row, col } = getTileFromMouseEvent(e);
    const tileId = `${row}_${col}`;

    // Boundaries check
    if (row < 0 || row >= GRID_ROWS || col < 0 || col >= GRID_COLS) return;


    const previous = gridState.current[tileId] || null;

    // ── Three-way toggle ──────────────────────────────────────────────────
    if (previous && previous.userId === currentUser.userId) {
      // ► Own tile → optimistic decolor (show as empty immediately)
      gridState.current[tileId] = null;
      lastClickedTileId.current = tileId;
      drawTile(ctx.current, row, col, null, false);
      updateMiniMap();

      if (stompClient.current && stompClient.current.connected) {
        stompClient.current.publish({
          destination: '/app/claim',
          body: JSON.stringify({ tileId, userId: currentUser.userId })
        });
      }

      // Revert if no server confirmation in 3s
      pendingClaims.current[tileId] = setTimeout(() => {
        gridState.current[tileId] = previous;
        drawTile(ctx.current, row, col, previous);
        updateMiniMap();
        delete pendingClaims.current[tileId];
      }, 3000);

    } else if (!previous) {
      // ► Empty tile → optimistic claim (color it)
      const optimisticTile = {
        userId: currentUser.userId,
        username: currentUser.username,
        color: currentUser.color,
        claimedAt: new Date().toISOString()
      };
      gridState.current[tileId] = optimisticTile;
      lastClickedTileId.current = tileId;
      drawTile(ctx.current, row, col, optimisticTile, true);
      updateMiniMap();

      if (stompClient.current && stompClient.current.connected) {
        stompClient.current.publish({
          destination: '/app/claim',
          body: JSON.stringify({ tileId, userId: currentUser.userId })
        });
      }

      // Revert if no server confirmation in 3s
      pendingClaims.current[tileId] = setTimeout(() => {
        gridState.current[tileId] = previous;
        drawTile(ctx.current, row, col, previous);
        updateMiniMap();
        delete pendingClaims.current[tileId];
      }, 3000);

    } else {
      // ► Someone else's tile → do nothing (blocked)
      return;
    }
  }

  // ── Mouse Listeners for Hover and Tooltip ───────────────────────────────────

  function handleTooltipAndHover(e) {
    const { row, col } = getTileFromMouseEvent(e);
    const isInside = row >= 0 && row < GRID_ROWS && col >= 0 && col < GRID_COLS;

    // Handle Hover Glow on canvas (drawing/removing white border on mouseover)
    if (ctx.current) {
      const currentHoverKey = isInside ? `${row}_${col}` : null;
      const lastHoverKey = lastHoveredTile.current 
        ? `${lastHoveredTile.current.row}_${lastHoveredTile.current.col}` 
        : null;

      if (currentHoverKey !== lastHoverKey) {
        // Restore last hovered tile
        if (lastHoveredTile.current) {
          const { row: lr, col: lc } = lastHoveredTile.current;
          const oldData = gridState.current[lastHoverKey];
          drawTile(ctx.current, lr, lc, oldData, false);
        }

        // Draw highlight border on new hovered tile
        if (isInside) {
          const newData = gridState.current[currentHoverKey];
          drawTile(ctx.current, row, col, newData, true);
          lastHoveredTile.current = { row, col };
        } else {
          lastHoveredTile.current = null;
        }
      }
    }

    // Tooltip update
    if (isInside) {
      const tileId = `${row}_${col}`;
      const tileData = gridState.current[tileId];
      if (tileData) {
        setTooltip({
          x: e.clientX + 12,
          y: e.clientY + 12,
          owner: tileData.username,
          color: tileData.color,
          claimedAt: tileData.claimedAt,
          tileId
        });
      } else {
        setTooltip(null);
      }
    } else {
      setTooltip(null);
    }
  }

  function handleMouseLeave() {
    // Clean up hover states on leave
    if (lastHoveredTile.current && ctx.current) {
      const { row, col } = lastHoveredTile.current;
      const tileId = `${row}_${col}`;
      const oldData = gridState.current[tileId];
      drawTile(ctx.current, row, col, oldData, false);
      lastHoveredTile.current = null;
    }
    setTooltip(null);
  }

  // ── Zoom and Pan Mouse Event Handlers ───────────────────────────────────────

  function handleWheel(e) {
    e.preventDefault();
    const MIN_SCALE = 0.5;
    const MAX_SCALE = 4.0;
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setViewport(v => ({
      ...v,
      scale: Math.min(MAX_SCALE, Math.max(MIN_SCALE, v.scale * delta))
    }));
  }

  function handleMouseDown(e) {
    if (e.button === 0 || e.button === 1 || e.altKey) {
      isMouseDown.current = true;
      dragging.current = false;
      dragStart.current = { x: e.clientX - viewport.x, y: e.clientY - viewport.y };
      mouseDownPos.current = { x: e.clientX, y: e.clientY };
    }
  }

  function handleMouseMove(e) {
    if (isMouseDown.current) {
      const dx = e.clientX - mouseDownPos.current.x;
      const dy = e.clientY - mouseDownPos.current.y;
      const dist = Math.hypot(dx, dy);

      if (dist > 4) {
        dragging.current = true;
        wasDragging.current = true;
        if (containerRef.current) {
          containerRef.current.style.cursor = 'grabbing';
        }
        const newX = e.clientX - dragStart.current.x;
        const newY = e.clientY - dragStart.current.y;
        setViewport(v => ({ ...v, x: newX, y: newY }));
      }
    } else {
      handleTooltipAndHover(e);
    }
  }

  function handleMouseUp(e) {
    isMouseDown.current = false;
    if (dragging.current) {
      dragging.current = false;
      if (containerRef.current) {
        containerRef.current.style.cursor = 'crosshair';
      }
      setTimeout(() => {
        wasDragging.current = false;
      }, 0);
    }
  }

  return (
    <div
      ref={containerRef}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseLeave}
      style={{
        width: '100%',
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        cursor: 'crosshair',
        background: '#e0f2fe',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      {/* Scaled/Translated viewport container */}
      <div
        style={{
          transform: `translate(${viewport.x}px, ${viewport.y}px) scale(${viewport.scale})`,
          transformOrigin: '0 0',
          willChange: 'transform',
          position: 'absolute',
          left: '50%',
          top: '50%',
          marginLeft: `-${CANVAS_PIXEL_W / 2}px`,
          marginTop: `-${CANVAS_PIXEL_H / 2}px`,
          width: `${CANVAS_PIXEL_W}px`,
          height: `${CANVAS_PIXEL_H}px`,
          boxShadow: '0 12px 48px rgba(34, 51, 84, 0.15)',
          background: '#f8fafc',
        }}
      >
        <canvas
          ref={canvasRef}
          width={CANVAS_PIXEL_W}
          height={CANVAS_PIXEL_H}
          onClick={handleCanvasClick}
          style={{
            display: 'block',
            width: '100%',
            height: '100%',
          }}
        />
      </div>

      {/* Mini Map */}
      <div
        style={{
          position: 'absolute',
          bottom: '24px',
          right: '156px',
          width: '52px',
          height: '52px',
          border: '1px solid rgba(251, 146, 60, 0.35)',
          background: '#fff7ed',
          borderRadius: '8px',
          overflow: 'hidden',
          boxShadow: '0 4px 12px rgba(34, 51, 84, 0.1)',
          zIndex: 10,
        }}
      >
        <canvas
          ref={miniMapRef}
          width={50}
          height={50}
          style={{ display: 'block', width: '100%', height: '100%' }}
        />
      </div>
    </div>
  );
}
