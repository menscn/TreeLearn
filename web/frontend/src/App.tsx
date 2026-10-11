import { I18nProvider } from './i18n/I18nProvider';
import { Router } from './pages/Router';

export function App() {
  return (
    <I18nProvider>
      <Router />
    </I18nProvider>
  );
}