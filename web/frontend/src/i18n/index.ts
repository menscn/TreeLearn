import { enUS } from './en-US';
import { zhCN } from './zh-CN';

export const LOCALES = ['zh-CN', 'en-US'] as const;
export type Locale = (typeof LOCALES)[number];
export const DEFAULT_LOCALE: Locale = 'zh-CN';

export type TranslationKey = keyof typeof zhCN;

const DICTIONARIES: Record<Locale, Record<TranslationKey, string>> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

/**
 * Resolves user copy at render time. A missing key throws in development so it
 * cannot be missed during review, and falls back to a visible marker in
 * production instead of silently rendering an empty string.
 */
export function translate(locale: Locale, key: TranslationKey): string {
  const value = DICTIONARIES[locale][key];
  if (value !== undefined) {
    return value;
  }
  if (import.meta.env.DEV) {
    throw new Error(`missing i18n key "${key}" for locale "${locale}"`);
  }
  return `[[${key}]]`;
}

export function keysOf(locale: Locale): string[] {
  return Object.keys(DICTIONARIES[locale]).sort();
}