import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { DEFAULT_LOCALE, translate, type Locale, type TranslationKey } from './index';

interface I18nValue {
  locale: Locale;
  t: (key: TranslationKey) => string;
  setLocale: (locale: Locale) => void;
}

const I18nContext = createContext<I18nValue | null>(null);

interface I18nProviderProps {
  children: ReactNode;
  initialLocale?: Locale;
}

export function I18nProvider({ children, initialLocale = DEFAULT_LOCALE }: I18nProviderProps) {
  const [locale, setLocale] = useState<Locale>(initialLocale);
  const value = useMemo<I18nValue>(
    () => ({
      locale,
      t: (key: TranslationKey) => translate(locale, key),
      setLocale,
    }),
    [locale],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const value = useContext(I18nContext);
  if (value === null) {
    throw new Error('useI18n must be used inside I18nProvider');
  }
  return value;
}