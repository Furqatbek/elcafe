import { useTranslation } from 'react-i18next';
import { errorMessage } from '../lib/errors';

/**
 * EH-2.4 (docs/ERROR_HANDLING_PLAN.md): the standard loading / error(+retry) / empty wrapper.
 * Pages render their content as children and hand this the useApiCall state, so every list and
 * detail view has the same three states — no more blank panels on a failed fetch.
 *
 *   <QueryState loading={loading} error={error} onRetry={refetch} empty={items.length === 0}>
 *     {items.map(...)}
 *   </QueryState>
 */
export default function QueryState({
  loading,
  error,
  empty = false,
  onRetry,
  children,
  loadingFallback,
  emptyMessage,
}) {
  const { t } = useTranslation();

  if (loading) {
    return loadingFallback ?? (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        {t('common.loading', 'Loading…')}
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
        <p className="text-muted-foreground max-w-md">{errorMessage(error)}</p>
        {onRetry && (
          <button
            onClick={() => onRetry()}
            className="px-4 py-2 rounded-md border text-sm font-medium hover:bg-accent"
          >
            {t('common.retry', 'Try again')}
          </button>
        )}
      </div>
    );
  }

  if (empty) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        {emptyMessage ?? t('common.noData', 'Nothing here yet')}
      </div>
    );
  }

  return children;
}
