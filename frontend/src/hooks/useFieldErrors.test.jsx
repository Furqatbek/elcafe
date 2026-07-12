import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useFieldErrors } from './useFieldErrors';

// Pins EH-2.6: per-field validation messages are extracted from a VALIDATION_ERROR response and
// exposed by field name, and cleared on demand.

const validationError = (fields) => ({
  response: { status: 400, data: { error: 'VALIDATION_ERROR', message: 'Validation failed', errors: fields } },
});

describe('useFieldErrors', () => {
  it('extracts fieldErrors and returns true when present', () => {
    const { result } = renderHook(() => useFieldErrors());
    let had;
    act(() => { had = result.current.setFromError(validationError({ email: 'must not be blank' })); });
    expect(had).toBe(true);
    expect(result.current.get('email')).toBe('must not be blank');
    expect(result.current.get('password')).toBeNull();
  });

  it('returns false and clears when the error carries no field detail', () => {
    const { result } = renderHook(() => useFieldErrors());
    act(() => { result.current.setFromError(validationError({ email: 'x' })); });
    let had;
    act(() => { had = result.current.setFromError({ response: { status: 500, data: { error: 'INTERNAL' } } }); });
    expect(had).toBe(false);
    expect(result.current.get('email')).toBeNull();
  });

  it('clear() removes all field errors', () => {
    const { result } = renderHook(() => useFieldErrors());
    act(() => { result.current.setFromError(validationError({ name: 'required' })); });
    act(() => { result.current.clear(); });
    expect(result.current.get('name')).toBeNull();
  });
});
