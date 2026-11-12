// Адрес сервера Star Guard по умолчанию. Пользователь меняет его в настройках расширения;
// при сборке архива (scripts/pack-extension.sh) сюда подставляется STAR_GUARD_API_URL.
// self — и window на страницах расширения, и глобальный объект service worker.
self.STAR_GUARD_CONFIG = { apiBase: 'http://localhost:8080' };
