/**
 * How a floor-map shape is drawn, and the palette the room is painted with (V184).
 *
 * Kept out of the components so the canvas, the inspector's shape picker and the tests all agree on
 * what "ROUNDED" looks like — a picker that previews a different corner than the canvas draws is worse
 * than no picker at all.
 */

/**
 * The four corner styles the operator asked for. The names match the backend enum
 * (FloorObject.Shape) exactly — they are sent as-is and parsed there, so renaming one here silently
 * breaks the save.
 */
export const SHAPES = ['RECTANGLE', 'ROUNDED', 'OVAL', 'SQUARE'];

/** Furniture the editor can drop onto a map. Matches FloorObject.ObjectType. */
export const OBJECT_TYPES = ['SOFA', 'CHAIR', 'PLANT', 'DOOR', 'BAR', 'WALL', 'OTHER'];

/** Sensible starting sizes so a dropped sofa does not appear as a 60x60 square like everything else. */
export const OBJECT_DEFAULT_SIZE = {
  SOFA: { width: 140, height: 60 },
  CHAIR: { width: 40, height: 40 },
  PLANT: { width: 40, height: 40 },
  DOOR: { width: 80, height: 16 },
  BAR: { width: 260, height: 70 },
  WALL: { width: 200, height: 12 },
  OTHER: { width: 60, height: 60 },
};

/**
 * Corner radius for a rectangular shape. OVAL is not here — it is drawn as an ellipse, because a
 * "fully rounded rectangle" and an ellipse are visibly different at a table's proportions.
 */
export function cornerRadius(shape, width, height) {
  const smallest = Math.min(width, height);
  switch (shape) {
    case 'SQUARE':
      return 0; // deliberately sharp — this is the "squared" corner style
    case 'ROUNDED':
      return Math.max(2, smallest * 0.22);
    case 'RECTANGLE':
    default:
      return 2; // a hair of softening so the stroke does not look like a pixel artefact
  }
}

export function isOval(shape) {
  return shape === 'OVAL';
}

/**
 * Table fill by live state. Occupancy wins over the stored status on purpose: the stored status is set
 * by hand and drifts, and a paid-up table still drawn red is how a host turns guests away from an
 * empty room.
 */
export function tableColors(table) {
  if (table.occupancy) {
    return { fill: '#fecaca', stroke: '#dc2626', text: '#7f1d1d', state: 'occupied' };
  }
  switch (table.status) {
    case 'RESERVED':
      return { fill: '#fde68a', stroke: '#d97706', text: '#78350f', state: 'reserved' };
    case 'CLEANING':
      return { fill: '#e0e7ff', stroke: '#6366f1', text: '#312e81', state: 'cleaning' };
    case 'OUT_OF_SERVICE':
      return { fill: '#e5e7eb', stroke: '#9ca3af', text: '#4b5563', state: 'outOfService' };
    default:
      return { fill: '#bbf7d0', stroke: '#16a34a', text: '#14532d', state: 'free' };
  }
}

export const OBJECT_COLORS = {
  SOFA: { fill: '#ddd6fe', stroke: '#7c3aed' },
  CHAIR: { fill: '#e2e8f0', stroke: '#64748b' },
  PLANT: { fill: '#d1fae5', stroke: '#059669' },
  DOOR: { fill: '#fef3c7', stroke: '#b45309' },
  BAR: { fill: '#fed7aa', stroke: '#ea580c' },
  WALL: { fill: '#cbd5e1', stroke: '#475569' },
  OTHER: { fill: '#f1f5f9', stroke: '#94a3b8' },
};

export function objectColors(objectType) {
  return OBJECT_COLORS[objectType] || OBJECT_COLORS.OTHER;
}

/** Editor grid, in the same abstract units as positions. */
export const GRID = 20;

export function snap(value, enabled) {
  if (!enabled) return Math.round(value);
  return Math.round(value / GRID) * GRID;
}

/** Mirrors FloorPlanService's bounds, so the editor refuses locally what the server would refuse anyway. */
export const MIN_DIMENSION = 1;
export const MAX_DIMENSION = 5000;

export function clampDimension(value) {
  return Math.min(MAX_DIMENSION, Math.max(MIN_DIMENSION, Math.round(value)));
}

/** A polygon as an SVG points attribute. */
export function polygonPoints(polygon) {
  if (!Array.isArray(polygon)) return '';
  return polygon.map((p) => `${p.x},${p.y}`).join(' ');
}

/** How long a party has been sitting, as a short "1h 20m" the host can read at a glance. */
export function seatedDuration(seatedSince, now = Date.now()) {
  if (!seatedSince) return null;
  const started = new Date(seatedSince).getTime();
  if (Number.isNaN(started)) return null;
  const minutes = Math.max(0, Math.floor((now - started) / 60000));
  if (minutes < 60) return `${minutes}m`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}
