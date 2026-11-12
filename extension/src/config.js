// Откуда расширение берёт адрес сервера Star Guard (по приоритету):
// 1. адрес, который пользователь вписал в настройках расширения;
// 2. server.json из репозитория (remoteConfigUrl) — правится на GitHub без пересборки и переустановки,
//    расширение перечитывает его раз в 5 минут;
// 3. apiBase ниже — запасной адрес (при сборке scripts/pack-extension.sh подставляет STAR_GUARD_API_URL).
// self — и window на страницах расширения, и глобальный объект service worker.
self.STAR_GUARD_CONFIG = {
  apiBase: 'http://201.51.23.42:8080',
  remoteConfigUrl: 'https://raw.githubusercontent.com/StarChecker/star-guard/main/server.json',
};
