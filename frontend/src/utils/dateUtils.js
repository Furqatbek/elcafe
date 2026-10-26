/**
 * Date utilities with Tashkent timezone (GMT+5)
 */

const TIMEZONE = 'Asia/Tashkent';

/**
 * Format date to locale string with Tashkent timezone
 * @param {Date|string} date - Date to format
 * @param {object} options - Intl.DateTimeFormat options
 * @returns {string} Formatted date string
 */
export const formatDateTime = (date, options = {}) => {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleString('ru-RU', {
    timeZone: TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    ...options,
  });
};

/**
 * Format date only (no time) with Tashkent timezone
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted date string
 */
export const formatDate = (date) => {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('ru-RU', {
    timeZone: TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
};

/**
 * Format time only (no date) with Tashkent timezone
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted time string
 */
export const formatTime = (date) => {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleTimeString('ru-RU', {
    timeZone: TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
  });
};

/**
 * Format time with seconds with Tashkent timezone
 * @param {Date|string} date - Date to format
 * @returns {string} Formatted time string
 */
export const formatTimeWithSeconds = (date) => {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleTimeString('ru-RU', {
    timeZone: TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
};

/**
 * Render a moment as the value an <input type="datetime-local"> expects, in Tashkent terms.
 * Timestamps from the API are naive wall-clock times already, so a string only needs
 * trimming; a Date has to be moved out of the browser's own timezone first.
 * @param {Date|string} date - Date, API timestamp, or empty value
 * @returns {string} Value for a datetime-local input, or '' when there is no date
 */
export const toDateTimeInputValue = (date) => {
  if (!date) return '';
  if (typeof date === 'string') return date.slice(0, 16);
  // 'sv-SE' renders as "YYYY-MM-DD HH:MM", which is the input format bar the separator.
  return date.toLocaleString('sv-SE', {
    timeZone: TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).replace(' ', 'T');
};

/**
 * Pass a datetime-local input value to the API as the LocalDateTime the backend parses.
 * The backend keeps the wall clock and drops any timezone, so converting through
 * new Date(value).toISOString() instead shifted the saved time by the browser's UTC
 * offset on every write.
 * @param {string} value - Value from a datetime-local input
 * @returns {string|null} Timestamp to send, or null when the field is empty
 */
export const toLocalDateTimeString = (value) => {
  if (!value) return null;
  // Browsers may leave the seconds off; both forms are valid ISO local date-times.
  return value.length === 16 ? `${value}:00` : value;
};

/**
 * Get current date/time in Tashkent timezone
 * @returns {Date} Current date
 */
export const nowInTashkent = () => {
  return new Date();
};

export default {
  formatDateTime,
  formatDate,
  formatTime,
  formatTimeWithSeconds,
  toDateTimeInputValue,
  toLocalDateTimeString,
  nowInTashkent,
  TIMEZONE,
};
