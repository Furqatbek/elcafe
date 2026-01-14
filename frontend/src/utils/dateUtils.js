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
  nowInTashkent,
  TIMEZONE,
};
