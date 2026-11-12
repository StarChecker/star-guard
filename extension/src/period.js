// Star Guard — общая модель периода анализа.
// Период — это либо короткое окно GH Archive в часах (1–24), либо окно истории
// GitHub в днях с необязательной датой окончания, либо вся история репозитория.
// Модуль общий для service worker, content script, popup, настроек и preview.
(function () {
  'use strict';

  const MAX_DAYS = 3650;
  const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
  const PRESETS = [
    { id: 'h6', kind: 'hours', hours: 6, short: '6 ч', long: '6 часов' },
    { id: 'h12', kind: 'hours', hours: 12, short: '12 ч', long: '12 часов' },
    { id: 'h24', kind: 'hours', hours: 24, short: '24 ч', long: '24 часа' },
    { id: 'd7', kind: 'days', days: 7, short: '7 дней', long: 'неделя' },
    { id: 'd30', kind: 'days', days: 30, short: '30 дней', long: 'месяц (30 дней)' },
    { id: 'd90', kind: 'days', days: 90, short: '90 дней', long: 'квартал (90 дней)' },
    { id: 'd180', kind: 'days', days: 180, short: '6 месяцев', long: 'пол года (180 дней)' },
    { id: 'd365', kind: 'days', days: 365, short: '1 год', long: 'год (365 дней)' },
    { id: 'all', kind: 'all', short: 'всё время', long: 'вся история репозитория' },
  ];

  function clampHours(value, fallback) {
    const hours = Math.round(Number(value));
    const base = Number.isFinite(hours) ? hours : Number(fallback);
    return Math.min(24, Math.max(1, Number.isFinite(base) ? base : 6));
  }

  function normalize(raw, fallbackHours) {
    if (raw && typeof raw === 'object') {
      if (raw.kind === 'all') {
        return { kind: 'all' };
      }
      if (raw.kind === 'days') {
        const days = Math.min(MAX_DAYS, Math.max(1, Math.round(Number(raw.days) || 30)));
        return raw.to && DATE_RE.test(raw.to) ? { kind: 'days', days, to: raw.to } : { kind: 'days', days };
      }
      if (raw.kind === 'hours') {
        return { kind: 'hours', hours: clampHours(raw.hours, fallbackHours) };
      }
    }
    return { kind: 'hours', hours: clampHours(fallbackHours, 6) };
  }

  function presetById(id) {
    return PRESETS.find((preset) => preset.id === id) || null;
  }

  function fromPreset(id) {
    const preset = presetById(id);
    if (!preset) return null;
    if (preset.kind === 'hours') return { kind: 'hours', hours: preset.hours };
    if (preset.kind === 'days') return { kind: 'days', days: preset.days };
    return { kind: 'all' };
  }

  /** Ближайший пресет для периода или null, если период задан вручную. */
  function presetId(period) {
    if (!period) return null;
    if (period.kind === 'all') return 'all';
    if (period.kind === 'hours') {
      const preset = PRESETS.find((p) => p.kind === 'hours' && p.hours === period.hours);
      return preset ? preset.id : null;
    }
    if (period.kind === 'days' && !period.to) {
      const preset = PRESETS.find((p) => p.kind === 'days' && p.days === period.days);
      return preset ? preset.id : null;
    }
    return null;
  }

  function key(period) {
    if (period.kind === 'all') return 'all';
    if (period.kind === 'days') return 'd' + period.days + (period.to ? '@' + period.to : '');
    return 'h' + period.hours;
  }

  function query(period) {
    if (period.kind === 'all') return 'all=true';
    if (period.kind === 'days') {
      return 'days=' + period.days + (period.to ? '&to=' + encodeURIComponent(period.to) : '');
    }
    return 'hours=' + period.hours;
  }

  function body(period) {
    if (period.kind === 'all') return { all: true };
    if (period.kind === 'days') return period.to ? { days: period.days, to: period.to } : { days: period.days };
    return { hours: period.hours };
  }

  /** Короткое окно для сводок индекса: батч и точки в списках умеют только часы. */
  function indexHours(period, fallbackHours) {
    return period.kind === 'hours' ? period.hours : clampHours(fallbackHours, 6);
  }

  const api = { MAX_DAYS, DATE_RE, PRESETS, clampHours, normalize, presetById, fromPreset, presetId, key, query, body, indexHours };
  if (typeof window !== 'undefined') {
    window.StarGuardPeriod = api;
  }
  if (typeof self !== 'undefined') {
    self.StarGuardPeriod = api;
  }
})();
