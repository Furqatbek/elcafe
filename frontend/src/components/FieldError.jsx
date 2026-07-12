/**
 * EH-2.6 (docs/ERROR_HANDLING_PLAN.md): the small inline message rendered under a form input when
 * the backend returns a per-field validation error. Renders nothing when there's no message.
 */
export default function FieldError({ message }) {
  if (!message) return null;
  return <p className="text-sm text-destructive mt-1">{message}</p>;
}
