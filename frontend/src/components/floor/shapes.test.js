import { describe, it, expect } from 'vitest';
import {
  GRID,
  MAX_DIMENSION,
  clampDimension,
  cornerRadius,
  isOval,
  objectColors,
  polygonPoints,
  seatedDuration,
  snap,
  tableColors,
} from './shapes';

// The map's arithmetic, asserted as arithmetic. jsdom has no SVG layout engine, so driving these
// through a rendered canvas would prove nothing about the numbers actually used to draw the room.

describe('corner shapes', () => {
  it('SQUARE is genuinely sharp — that is what "squared" means to the operator', () => {
    expect(cornerRadius('SQUARE', 100, 100)).toBe(0);
  });

  it('ROUNDED softens proportionally to the smaller side, so a long bench stays a bench', () => {
    expect(cornerRadius('ROUNDED', 400, 60)).toBeCloseTo(60 * 0.22);
    expect(cornerRadius('ROUNDED', 60, 400)).toBeCloseTo(60 * 0.22);
  });

  it('an unknown shape falls back to a rectangle rather than rendering nothing', () => {
    expect(cornerRadius('TRIANGLE', 100, 100)).toBe(cornerRadius('RECTANGLE', 100, 100));
  });

  it('OVAL is drawn as an ellipse, not as a very round rectangle', () => {
    expect(isOval('OVAL')).toBe(true);
    expect(isOval('ROUNDED')).toBe(false);
  });
});

describe('snapping', () => {
  it('snaps to the nearest grid line when enabled', () => {
    expect(snap(37, true)).toBe(GRID * 2); // 37 is nearer 40 than 20
    expect(snap(29, true)).toBe(GRID); //     29 is nearer 20 than 40
    expect(snap(31, true)).toBe(GRID * 2); // and 31 tips the other way
    expect(snap(9, true)).toBe(0);
  });

  it('leaves the value alone (bar rounding) when disabled — free placement must stay free', () => {
    expect(snap(37.4, false)).toBe(37);
    expect(snap(29.6, false)).toBe(30);
  });
});

describe('dimension clamping', () => {
  it('refuses a zero or negative size, which would render as an invisible unclickable shape', () => {
    expect(clampDimension(0)).toBe(1);
    expect(clampDimension(-40)).toBe(1);
  });

  it('caps an absurd size at the same bound the server enforces', () => {
    expect(clampDimension(999999)).toBe(MAX_DIMENSION);
  });
});

describe('table colours', () => {
  it('an open order wins over the stored status — a paid-up table must not stay red', () => {
    expect(tableColors({ status: 'AVAILABLE', occupancy: { orderId: 1 } }).state).toBe('occupied');
    expect(tableColors({ status: 'OCCUPIED', occupancy: null }).state).toBe('free');
  });

  it('reserved and cleaning are distinguishable from both free and occupied', () => {
    expect(tableColors({ status: 'RESERVED' }).state).toBe('reserved');
    expect(tableColors({ status: 'CLEANING' }).state).toBe('cleaning');
    expect(tableColors({ status: 'OUT_OF_SERVICE' }).state).toBe('outOfService');
  });

  it('an unknown object type still gets a colour instead of an undefined fill', () => {
    expect(objectColors('SPACESHIP').fill).toBeTruthy();
  });
});

describe('polygon rendering', () => {
  it('renders points in SVG order', () => {
    expect(polygonPoints([{ x: 0, y: 0 }, { x: 10, y: 20 }])).toBe('0,0 10,20');
  });

  it('a missing polygon renders as empty rather than throwing mid-paint', () => {
    expect(polygonPoints(undefined)).toBe('');
    expect(polygonPoints(null)).toBe('');
  });
});

describe('seated duration', () => {
  const base = new Date('2026-07-20T20:00:00Z').getTime();

  it('reads as minutes under an hour and hours plus minutes above', () => {
    expect(seatedDuration('2026-07-20T19:35:00Z', base)).toBe('25m');
    expect(seatedDuration('2026-07-20T18:30:00Z', base)).toBe('1h 30m');
  });

  it('never shows a negative duration from a clock skew between server and browser', () => {
    expect(seatedDuration('2026-07-20T20:05:00Z', base)).toBe('0m');
  });

  it('returns nothing when there is no seating time to report', () => {
    expect(seatedDuration(null)).toBeNull();
    expect(seatedDuration('not a date')).toBeNull();
  });
});
