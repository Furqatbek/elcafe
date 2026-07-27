import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  GRID,
  clampDimension,
  cornerRadius,
  isOval,
  objectColors,
  polygonPoints,
  snap,
  tableColors,
} from './shapes';

/**
 * The room itself (V184): an SVG the operator can pan, zoom, and — with the edit gate open — drag,
 * resize and rotate.
 *
 * <p>SVG rather than canvas because every shape here needs to be an addressable element: a table has to
 * be clickable to open its guest, and a keyboard user has to be able to reach it. On a canvas all of
 * that would have to be rebuilt by hand from hit-testing maths.
 *
 * <p>Screen coordinates are converted to map coordinates through the SVG's own CTM rather than by
 * dividing by the zoom factor. The map is inside a scrollable, responsive container, so a hand-rolled
 * conversion drifts the moment the page layout changes — the shape then jumps away from the cursor.
 */
export default function FloorCanvas({
  plan,
  editMode,
  snapToGrid,
  selected,
  onSelect,
  onTableClick,
  onGeometryChange,
  drawingSection,
  draftPolygon,
  onPolygonPoint,
}) {
  const { t } = useTranslation();
  const svgRef = useRef(null);
  const [view, setView] = useState({ zoom: 1, x: 0, y: 0 });
  // What the pointer is currently doing. Kept in a ref, not state: a drag fires on every mouse move and
  // re-rendering the whole room on each frame makes dragging visibly lag behind the cursor.
  const gesture = useRef(null);

  const canvasWidth = plan?.canvasWidth || 1200;
  const canvasHeight = plan?.canvasHeight || 800;

  /** Screen point → map point, via the live transform matrix. */
  const toMapPoint = useCallback((clientX, clientY) => {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0 };
    const point = svg.createSVGPoint ? svg.createSVGPoint() : null;
    if (!point || !svg.getScreenCTM) {
      return { x: clientX, y: clientY }; // jsdom and very old browsers: geometry is not exercised there
    }
    point.x = clientX;
    point.y = clientY;
    const ctm = svg.getScreenCTM();
    if (!ctm) return { x: clientX, y: clientY };
    const mapped = point.matrixTransform(ctm.inverse());
    return { x: mapped.x, y: mapped.y };
  }, []);

  const zoomBy = useCallback((factor) => {
    setView((v) => ({ ...v, zoom: Math.min(3, Math.max(0.25, v.zoom * factor)) }));
  }, []);

  const resetView = useCallback(() => setView({ zoom: 1, x: 0, y: 0 }), []);

  // Wheel-zoom is registered natively rather than via onWheel, because React attaches wheel listeners
  // as passive — preventDefault() there is ignored and the whole page scrolls while you zoom the map.
  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return undefined;
    const onWheel = (e) => {
      if (!e.ctrlKey && !e.metaKey && !e.shiftKey) {
        // Plain scroll keeps scrolling the page — hijacking it traps the user inside the map.
        return;
      }
      e.preventDefault();
      zoomBy(e.deltaY < 0 ? 1.1 : 1 / 1.1);
    };
    svg.addEventListener('wheel', onWheel, { passive: false });
    return () => svg.removeEventListener('wheel', onWheel);
  }, [zoomBy]);

  const beginGesture = (e, kind, item, itemKind, handle) => {
    if (!editMode) return;
    e.stopPropagation();
    const start = toMapPoint(e.clientX, e.clientY);
    gesture.current = {
      kind,
      handle,
      itemKind,
      id: item.id,
      startX: start.x,
      startY: start.y,
      origin: {
        positionX: item.positionX || 0,
        positionY: item.positionY || 0,
        width: item.width || 60,
        height: item.height || 60,
        rotationDeg: item.rotationDeg || 0,
      },
    };
    onSelect({ kind: itemKind, id: item.id });
  };

  const onPointerMove = (e) => {
    const g = gesture.current;
    if (!g) return;
    const now = toMapPoint(e.clientX, e.clientY);
    const dx = now.x - g.startX;
    const dy = now.y - g.startY;

    if (g.kind === 'move') {
      onGeometryChange(g.itemKind, g.id, {
        positionX: snap(g.origin.positionX + dx, snapToGrid),
        positionY: snap(g.origin.positionY + dy, snapToGrid),
      });
    } else if (g.kind === 'resize') {
      onGeometryChange(g.itemKind, g.id, {
        width: clampDimension(snap(g.origin.width + dx, snapToGrid)),
        height: clampDimension(snap(g.origin.height + dy, snapToGrid)),
      });
    } else if (g.kind === 'rotate') {
      const cx = g.origin.positionX + g.origin.width / 2;
      const cy = g.origin.positionY + g.origin.height / 2;
      const angle = (Math.atan2(now.y - cy, now.x - cx) * 180) / Math.PI + 90;
      const wrapped = ((Math.round(angle) % 360) + 360) % 360;
      // Shift snaps to 15°, which is what you want for aligning a row of tables against a wall.
      onGeometryChange(g.itemKind, g.id, {
        rotationDeg: e.shiftKey ? Math.round(wrapped / 15) * 15 : wrapped,
      });
    } else if (g.kind === 'pan') {
      setView((v) => ({ ...v, x: g.origin.positionX + dx, y: g.origin.positionY + dy }));
    }
  };

  const endGesture = () => {
    gesture.current = null;
  };

  const onBackgroundDown = (e) => {
    if (drawingSection) {
      const point = toMapPoint(e.clientX, e.clientY);
      onPolygonPoint({ x: Math.round(snap(point.x, snapToGrid)), y: Math.round(snap(point.y, snapToGrid)) });
      return;
    }
    const start = toMapPoint(e.clientX, e.clientY);
    gesture.current = {
      kind: 'pan',
      startX: start.x,
      startY: start.y,
      origin: { positionX: view.x, positionY: view.y },
    };
    onSelect(null);
  };

  const isSelected = (kind, id) => selected && selected.kind === kind && selected.id === id;

  const renderHandles = (item, kind) => {
    if (!editMode || !isSelected(kind, item.id)) return null;
    const x = item.positionX || 0;
    const y = item.positionY || 0;
    const w = item.width || 60;
    const h = item.height || 60;
    return (
      <g>
        {/* Bottom-right corner resizes; the knob above the shape rotates. */}
        <rect
          data-testid={`resize-${kind}-${item.id}`}
          x={x + w - 5}
          y={y + h - 5}
          width={10}
          height={10}
          fill="#2563eb"
          stroke="#fff"
          strokeWidth={1.5}
          style={{ cursor: 'nwse-resize' }}
          onMouseDown={(e) => beginGesture(e, 'resize', item, kind)}
        />
        <line x1={x + w / 2} y1={y} x2={x + w / 2} y2={y - 22} stroke="#2563eb" strokeWidth={1.5} />
        <circle
          data-testid={`rotate-${kind}-${item.id}`}
          cx={x + w / 2}
          cy={y - 24}
          r={6}
          fill="#2563eb"
          stroke="#fff"
          strokeWidth={1.5}
          style={{ cursor: 'grab' }}
          onMouseDown={(e) => beginGesture(e, 'rotate', item, kind)}
        />
      </g>
    );
  };

  const renderShape = (item, colors, extraProps = {}) => {
    const x = item.positionX || 0;
    const y = item.positionY || 0;
    const w = item.width || 60;
    const h = item.height || 60;
    const common = {
      fill: colors.fill,
      stroke: colors.stroke,
      strokeWidth: 2,
      ...extraProps,
    };
    if (isOval(item.shape)) {
      return <ellipse cx={x + w / 2} cy={y + h / 2} rx={w / 2} ry={h / 2} {...common} />;
    }
    return <rect x={x} y={y} width={w} height={h} rx={cornerRadius(item.shape, w, h)} {...common} />;
  };

  const tables = plan?.tables || [];
  const objects = plan?.objects || [];
  const sections = plan?.sections || [];

  return (
    <div className="relative w-full">
      <div className="absolute right-3 top-3 z-10 flex flex-col gap-1 rounded-lg bg-white/90 p-1 shadow">
        <button
          type="button"
          onClick={() => zoomBy(1.2)}
          aria-label={t('floorPlan.zoomIn', 'Zoom in')}
          className="h-8 w-8 rounded text-lg font-semibold hover:bg-gray-100"
        >
          +
        </button>
        <button
          type="button"
          onClick={() => zoomBy(1 / 1.2)}
          aria-label={t('floorPlan.zoomOut', 'Zoom out')}
          className="h-8 w-8 rounded text-lg font-semibold hover:bg-gray-100"
        >
          −
        </button>
        <button
          type="button"
          onClick={resetView}
          aria-label={t('floorPlan.resetView', 'Reset view')}
          className="h-8 w-8 rounded text-xs hover:bg-gray-100"
        >
          1:1
        </button>
      </div>
      <div className="absolute left-3 top-3 z-10 rounded bg-white/90 px-2 py-1 text-xs text-gray-600 shadow">
        {Math.round(view.zoom * 100)}%
      </div>

      <div className="w-full overflow-auto rounded-lg border bg-slate-50" style={{ maxHeight: '70vh' }}>
        <svg
          ref={svgRef}
          data-testid="floor-canvas"
          role="img"
          aria-label={t('floorPlan.canvasLabel', 'Restaurant floor map')}
          width={canvasWidth * view.zoom}
          height={canvasHeight * view.zoom}
          viewBox={`0 0 ${canvasWidth} ${canvasHeight}`}
          onMouseDown={onBackgroundDown}
          onMouseMove={onPointerMove}
          onMouseUp={endGesture}
          onMouseLeave={endGesture}
          style={{ cursor: drawingSection ? 'crosshair' : 'grab', display: 'block' }}
        >
          <defs>
            <pattern id="floor-grid" width={GRID} height={GRID} patternUnits="userSpaceOnUse">
              <path d={`M ${GRID} 0 L 0 0 0 ${GRID}`} fill="none" stroke="#e2e8f0" strokeWidth="1" />
            </pattern>
          </defs>
          <g transform={`translate(${view.x} ${view.y})`}>
            <rect width={canvasWidth} height={canvasHeight} fill="url(#floor-grid)" />

            {/* Sections paint first so tables and furniture sit on top of them, never under. */}
            {sections.map((section) => (
              <g key={`section-${section.id}`}>
                <polygon
                  data-testid={`section-${section.id}`}
                  points={polygonPoints(section.polygon)}
                  fill={section.fillColor || '#e0f2fe'}
                  fillOpacity={0.55}
                  stroke={isSelected('section', section.id) ? '#2563eb' : '#0284c7'}
                  strokeWidth={isSelected('section', section.id) ? 3 : 1.5}
                  strokeDasharray="6 4"
                  style={{ cursor: editMode ? 'pointer' : 'default' }}
                  onMouseDown={(e) => {
                    if (!editMode) return;
                    e.stopPropagation();
                    onSelect({ kind: 'section', id: section.id });
                  }}
                />
                {Array.isArray(section.polygon) && section.polygon.length > 0 && (
                  <text
                    x={section.polygon[0].x + 6}
                    y={section.polygon[0].y + 16}
                    fontSize={13}
                    fill="#0c4a6e"
                    style={{ pointerEvents: 'none' }}
                  >
                    {section.name}
                  </text>
                )}
              </g>
            ))}

            {/* The polygon being drawn right now, so the operator can see the shape taking form. */}
            {drawingSection && draftPolygon?.length > 0 && (
              <g>
                <polyline
                  points={polygonPoints(draftPolygon)}
                  fill="none"
                  stroke="#2563eb"
                  strokeWidth={2}
                  strokeDasharray="4 3"
                />
                {draftPolygon.map((p, i) => (
                  <circle key={`draft-${i}`} cx={p.x} cy={p.y} r={4} fill="#2563eb" />
                ))}
              </g>
            )}

            {objects.map((object) => {
              const colors = objectColors(object.objectType);
              const cx = (object.positionX || 0) + (object.width || 60) / 2;
              const cy = (object.positionY || 0) + (object.height || 60) / 2;
              return (
                <g
                  key={`object-${object.id}`}
                  data-testid={`object-${object.id}`}
                  transform={`rotate(${object.rotationDeg || 0} ${cx} ${cy})`}
                >
                  {renderShape(object, colors, {
                    style: { cursor: editMode ? 'move' : 'default' },
                    onMouseDown: (e) => beginGesture(e, 'move', object, 'object'),
                  })}
                  {object.label && (
                    <text
                      x={cx}
                      y={cy + 4}
                      textAnchor="middle"
                      fontSize={11}
                      fill="#334155"
                      style={{ pointerEvents: 'none' }}
                    >
                      {object.label}
                    </text>
                  )}
                  {renderHandles(object, 'object')}
                </g>
              );
            })}

            {tables.map((table) => {
              const colors = tableColors(table);
              const w = table.width || 100;
              const h = table.height || 100;
              const cx = (table.positionX || 0) + w / 2;
              const cy = (table.positionY || 0) + h / 2;
              return (
                <g
                  key={`table-${table.id}`}
                  data-testid={`table-${table.id}`}
                  data-state={colors.state}
                  transform={`rotate(${table.rotationDeg || 0} ${cx} ${cy})`}
                >
                  {renderShape(table, colors, {
                    style: { cursor: editMode ? 'move' : 'pointer' },
                    strokeWidth: isSelected('table', table.id) ? 3.5 : 2,
                    stroke: isSelected('table', table.id) ? '#2563eb' : colors.stroke,
                    onMouseDown: (e) => {
                      if (editMode) beginGesture(e, 'move', table, 'table');
                    },
                    onClick: (e) => {
                      if (editMode) return;
                      e.stopPropagation();
                      onTableClick(table);
                    },
                  })}
                  {/* A merged table is a satellite of another; marking it stops the host reading the
                      pair as two separate parties. */}
                  {table.mergedIntoTableId && (
                    <text
                      x={cx}
                      y={cy - h / 2 + 14}
                      textAnchor="middle"
                      fontSize={10}
                      fill="#6b21a8"
                      style={{ pointerEvents: 'none' }}
                    >
                      ⇢ {table.mergedIntoTableId}
                    </text>
                  )}
                  <text
                    x={cx}
                    y={cy + 1}
                    textAnchor="middle"
                    fontSize={14}
                    fontWeight="600"
                    fill={colors.text}
                    style={{ pointerEvents: 'none' }}
                  >
                    {table.tableNumber}
                  </text>
                  <text
                    x={cx}
                    y={cy + 16}
                    textAnchor="middle"
                    fontSize={10}
                    fill={colors.text}
                    style={{ pointerEvents: 'none' }}
                  >
                    {table.occupancy?.customerName || `${table.capacity || 0}p`}
                  </text>
                  {renderHandles(table, 'table')}
                </g>
              );
            })}
          </g>
        </svg>
      </div>
    </div>
  );
}
