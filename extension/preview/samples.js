// Пример ответов API для preview и тестов вёрстки (сгенерированы, не реальные данные).
window.STAR_GUARD_SAMPLES = {
 "farm": {
  "repo": {
   "owner": "quick-ai",
   "name": "gpt-agent-pro",
   "full_name": "quick-ai/gpt-agent-pro"
  },
  "window": {
   "from": "2026-11-10T09:00:00Z",
   "to": "2026-11-10T15:00:00Z",
   "hours": 6
  },
  "stats": {
   "total_events": 131,
   "stars": 94,
   "forks": 3,
   "pushes": 4,
   "issues": 1,
   "pull_requests": 0,
   "other": 2,
   "unique_actors": 97,
   "star_actors": 94,
   "peak_stars_10m": 26,
   "stars_by_hour": []
  },
  "score": {
   "score": 78,
   "level": "high",
   "verdict": "Заметные признаки накрутки: главный сигнал — «пересечение аккаунтов».",
   "confidence": 0.82,
   "insufficient_data": false,
   "mocked": true,
   "rules_applied": [
    "multi_trigger"
   ],
   "computed_at": "2026-11-10T15:00:00Z",
   "checks": [
    {
     "id": "burst",
     "title": "Всплески звёзд",
     "summary": "Демо-значение: реальная проверка ещё не подключена.",
     "score": 0.95,
     "confidence": 0.9,
     "weight": 0.3,
     "contribution": 0.285,
     "active": true,
     "error": null,
     "details": {
      "mocked": true,
      "profile": "suspicious"
     }
    },
    {
     "id": "fresh_accounts",
     "title": "Свежие и пустые аккаунты",
     "summary": "Нет данных о возрасте аккаунтов — проверка не участвует в оценке.",
     "score": 0.0,
     "confidence": 0.0,
     "weight": 0.22,
     "contribution": 0.0,
     "active": false,
     "error": null,
     "details": {
      "mocked": false,
      "reason": "no_actor_data"
     }
    },
    {
     "id": "engagement",
     "title": "Звёзды без активности",
     "summary": "На 94 звёзд — 8 действий (форки, issues, PR, пуши).",
     "score": 0.92,
     "confidence": 0.9,
     "weight": 0.18,
     "contribution": 0.166,
     "active": true,
     "error": null,
     "details": {
      "mocked": false,
      "stars": 94,
      "activity": 8
     }
    },
    {
     "id": "actor_overlap",
     "title": "Пересечение аккаунтов",
     "summary": "52 из 94 отметивших репозиторий поставили звёзды ещё ≥2 репозиториям за окно. Сильнее всего совпадает аудитория quick-ai/crypto-sniper-bot (81%).",
     "score": 1.0,
     "confidence": 0.8,
     "weight": 0.22,
     "contribution": 0.22,
     "active": true,
     "error": null,
     "details": {
      "mocked": false,
      "star_actors": 94,
      "multi_repo_actors": 52,
      "jaccard_max": 0.81,
      "top_overlaps": [
       {
        "repo": "quick-ai/crypto-sniper-bot",
        "jaccard": 0.81,
        "shared_actors": 47,
        "repo_star_actors": 50
       },
       {
        "repo": "free-tools/game-cheats-2026",
        "jaccard": 0.64,
        "shared_actors": 41,
        "repo_star_actors": 52
       },
       {
        "repo": "spring-projects/spring-boot",
        "jaccard": 0.01,
        "shared_actors": 2,
        "repo_star_actors": 118
       }
      ]
     }
    },
    {
     "id": "circadian",
     "title": "Суточный ритм",
     "summary": "Демо-значение: реальная проверка ещё не подключена.",
     "score": 0.6,
     "confidence": 0.9,
     "weight": 0.08,
     "contribution": 0.048,
     "active": true,
     "error": null,
     "details": {
      "mocked": true,
      "profile": "suspicious"
     }
    }
   ]
  },
  "archive": {
   "hours_requested": 6,
   "hours_loaded": 6,
   "missing_hours": [],
   "parse_errors": 0,
   "cached": true
  },
  "stars_timeline": {
   "bucket_minutes": 10,
   "estimated": false,
   "suspicious_stars": 52,
   "suspicious_by_check": {
    "actor_overlap": 52
   },
   "points": [
    {
     "at": "2026-11-10T09:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:10:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:20:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:30:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:40:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:50:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:10:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:20:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:30:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:40:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:50:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:10:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:20:00Z",
     "stars": 24,
     "suspicious": 24
    },
    {
     "at": "2026-11-10T11:30:00Z",
     "stars": 21,
     "suspicious": 19
    },
    {
     "at": "2026-11-10T11:40:00Z",
     "stars": 11,
     "suspicious": 9
    },
    {
     "at": "2026-11-10T11:50:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:10:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:20:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:30:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:40:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:50:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:00:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:10:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:20:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:30:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:40:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:50:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:10:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:20:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:30:00Z",
     "stars": 0,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:40:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:50:00Z",
     "stars": 0,
     "suspicious": 0
    }
   ]
  }
 },
 "healthy": {
  "repo": {
   "owner": "spring-projects",
   "name": "spring-boot",
   "full_name": "spring-projects/spring-boot"
  },
  "window": {
   "from": "2026-11-10T09:00:00Z",
   "to": "2026-11-10T15:00:00Z",
   "hours": 6
  },
  "stats": {
   "total_events": 4231,
   "stars": 169,
   "forks": 21,
   "pushes": 3110,
   "issues": 233,
   "pull_requests": 725,
   "other": 0,
   "unique_actors": 611,
   "star_actors": 169,
   "peak_stars_10m": 11,
   "stars_by_hour": []
  },
  "score": {
   "score": 7,
   "level": "low",
   "verdict": "Признаков накрутки не найдено.",
   "confidence": 0.86,
   "insufficient_data": false,
   "mocked": true,
   "rules_applied": [],
   "computed_at": "2026-11-10T15:00:00Z",
   "checks": [
    {
     "id": "burst",
     "title": "Всплески звёзд",
     "summary": "Демо-значение: реальная проверка ещё не подключена.",
     "score": 0.1,
     "confidence": 0.9,
     "weight": 0.3,
     "contribution": 0.03,
     "active": true,
     "error": null,
     "details": {
      "mocked": true
     }
    },
    {
     "id": "fresh_accounts",
     "title": "Свежие и пустые аккаунты",
     "summary": "Нет данных о возрасте аккаунтов — проверка не участвует в оценке.",
     "score": 0.0,
     "confidence": 0.0,
     "weight": 0.22,
     "contribution": 0.0,
     "active": false,
     "error": null,
     "details": {
      "mocked": false,
      "reason": "no_actor_data"
     }
    },
    {
     "id": "engagement",
     "title": "Звёзды без активности",
     "summary": "На 169 звёзд — 4089 действий (форки, issues, PR, пуши).",
     "score": 0.0,
     "confidence": 0.9,
     "weight": 0.18,
     "contribution": 0.0,
     "active": true,
     "error": null,
     "details": {
      "mocked": false
     }
    },
    {
     "id": "actor_overlap",
     "title": "Пересечение аккаунтов",
     "summary": "6 из 169 отметивших репозиторий поставили звёзды ещё ≥2 репозиториям за окно.",
     "score": 0.04,
     "confidence": 0.8,
     "weight": 0.22,
     "contribution": 0.009,
     "active": true,
     "error": null,
     "details": {
      "mocked": false,
      "top_overlaps": []
     }
    },
    {
     "id": "circadian",
     "title": "Суточный ритм",
     "summary": "Демо-значение: реальная проверка ещё не подключена.",
     "score": 0.1,
     "confidence": 0.9,
     "weight": 0.08,
     "contribution": 0.008,
     "active": true,
     "error": null,
     "details": {
      "mocked": true
     }
    }
   ]
  },
  "archive": {
   "hours_requested": 6,
   "hours_loaded": 6,
   "missing_hours": [],
   "parse_errors": 0,
   "cached": true
  },
  "stars_timeline": {
   "bucket_minutes": 10,
   "estimated": true,
   "suspicious_stars": 1,
   "suspicious_by_check": {
    "actor_overlap": 1
   },
   "points": [
    {
     "at": "2026-11-10T09:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:10:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:20:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:30:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:40:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T09:50:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:10:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:20:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:30:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:40:00Z",
     "stars": 7,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T10:50:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:10:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:20:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:30:00Z",
     "stars": 7,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:40:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T11:50:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:10:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:20:00Z",
     "stars": 8,
     "suspicious": 1
    },
    {
     "at": "2026-11-10T12:30:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:40:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T12:50:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:10:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:20:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:30:00Z",
     "stars": 7,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:40:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T13:50:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:10:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:20:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:30:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:40:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T14:50:00Z",
     "stars": 4,
     "suspicious": 0
    }
   ]
  }
 },
 "summaries": {
  "quick-ai/gpt-agent-pro": {
   "full_name": "quick-ai/gpt-agent-pro",
   "status": "ok",
   "score": 78,
   "level": "high",
   "confidence": 0.82,
   "verdict": "Заметные признаки накрутки: главный сигнал — «пересечение аккаунтов».",
   "stars": 94,
   "suspicious_stars": 52,
   "mocked": true
  },
  "spring-projects/spring-boot": {
   "full_name": "spring-projects/spring-boot",
   "status": "ok",
   "score": 7,
   "level": "low",
   "confidence": 0.86,
   "verdict": "Признаков накрутки не найдено.",
   "stars": 169,
   "suspicious_stars": 1,
   "mocked": true
  },
  "free-tools/game-cheats-2026": {
   "full_name": "free-tools/game-cheats-2026",
   "status": "ok",
   "score": 91,
   "level": "critical",
   "confidence": 0.8,
   "verdict": "Сильные признаки накрутки: главный сигнал — «пересечение аккаунтов».",
   "stars": 52,
   "suspicious_stars": 48,
   "mocked": true
  },
  "torvalds/linux": {
   "full_name": "torvalds/linux",
   "status": "ok",
   "score": 34,
   "level": "medium",
   "confidence": 0.7,
   "verdict": "Есть отдельные признаки накрутки: главный сигнал — «всплески звёзд».",
   "stars": 40,
   "suspicious_stars": 3,
   "mocked": true
  },
  "someone/small-lib": {
   "full_name": "someone/small-lib",
   "status": "not_indexed",
   "score": null,
   "level": null,
   "confidence": null,
   "verdict": null,
   "stars": null,
   "suspicious_stars": null,
   "mocked": false
  }
 }
};
