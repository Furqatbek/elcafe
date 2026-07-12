import { useState, useCallback } from 'react';
import { getAppError } from '../lib/errors';

/**
 * EH-2.6 (docs/ERROR_HANDLING_PLAN.md): hold per-field validation messages from a VALIDATION_ERROR
 * response so a form can render them under the matching inputs instead of a single toast.
 *
 *   const { fieldErrors, setFromError, clear, get } = useFieldErrors();
 *   catch (e) { setFromError(e); notifyError(e); }   // toast + inline field marks
 *   <Input ... /> {get('email') && <FieldError message={get('email')} />}
 *
 * setFromError returns true when the error carried field errors, so callers can decide whether the
 * generic toast is still worth showing.
 */
export function useFieldErrors() {
  const [fieldErrors, setFieldErrors] = useState({});

  const setFromError = useCallback((error) => {
    const app = getAppError(error);
    if (app.fieldErrors && typeof app.fieldErrors === 'object') {
      setFieldErrors(app.fieldErrors);
      return true;
    }
    setFieldErrors({});
    return false;
  }, []);

  const clear = useCallback(() => setFieldErrors({}), []);
  const get = useCallback((name) => fieldErrors?.[name] || null, [fieldErrors]);

  return { fieldErrors, setFromError, clear, get };
}
