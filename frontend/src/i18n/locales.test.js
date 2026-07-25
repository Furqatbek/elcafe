import { describe, it, expect } from 'vitest';
import en from './locales/en.json';
import ru from './locales/ru.json';
import uz from './locales/uz.json';

/** Flatten a locale bundle to { "a.b.c": "value" }. */
function flatten(node, prefix = '', out = {}) {
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      flatten(value, path, out);
    } else {
      out[path] = value;
    }
  }
  return out;
}

const EN = flatten(en);
const RU = flatten(ru);
const UZ = flatten(uz);

const CYRILLIC = /[Ѐ-ӿ]/;

describe('i18n locale bundles', () => {
  it('uz is written in the Latin alphabet — no Cyrillic characters', () => {
    // Uzbek here is Latin-script. Cyrillic in this bundle has always been contamination from
    // copy-pasting the Russian strings: it renders as visually-plausible nonsense ("Obunachilар"
    // reads as "Obunachilap"), so it survives review. Three such strings shipped in the Instagram
    // block before this test existed.
    const offenders = Object.entries(UZ)
      .filter(([, value]) => typeof value === 'string' && CYRILLIC.test(value))
      .map(([key, value]) => `${key} = ${JSON.stringify(value)}`);

    expect(offenders).toEqual([]);
  });

  it('ru and uz cover every key en defines', () => {
    // A missing key renders as the raw dotted path to the user.
    const enKeys = Object.keys(EN);
    expect(enKeys.filter((k) => !(k in RU))).toEqual([]);
    expect(enKeys.filter((k) => !(k in UZ))).toEqual([]);
  });

  it('interpolation placeholders match across locales', () => {
    // "{{count}}" dropped in a translation silently prints a sentence with the number missing.
    const placeholders = (s) =>
      typeof s === 'string' ? [...s.matchAll(/\{\{(\w+)\}\}/g)].map((m) => m[1]).sort() : [];

    const mismatches = [];
    for (const [key, value] of Object.entries(EN)) {
      const expected = placeholders(value);
      if (expected.length === 0) continue;
      for (const [name, bundle] of [['ru', RU], ['uz', UZ]]) {
        if (!(key in bundle)) continue;
        const actual = placeholders(bundle[key]);
        if (JSON.stringify(actual) !== JSON.stringify(expected)) {
          mismatches.push(`${name}:${key} expected ${expected} got ${actual}`);
        }
      }
    }
    expect(mismatches).toEqual([]);
  });
});
