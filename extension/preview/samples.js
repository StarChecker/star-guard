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
        "repo": "acme/web-framework",
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
  "source": {
   "type": "gh_archive_index",
   "stars_sampled": null,
   "stars_total": null,
   "coverage_from": null
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
   "owner": "acme",
   "name": "web-framework",
   "full_name": "acme/web-framework"
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
  "source": {
   "type": "gh_archive_index",
   "stars_sampled": null,
   "stars_total": null,
   "coverage_from": null
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
  "acme/web-framework": {
   "full_name": "acme/web-framework",
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
  "example/kernel-tools": {
   "full_name": "example/kernel-tools",
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
 },
 "history": {
  "repo": {
   "owner": "acme",
   "name": "cli-helper",
   "full_name": "acme/cli-helper"
  },
  "window": {
   "from": "2026-10-12T12:00:00Z",
   "to": "2026-11-12T00:00:00Z",
   "hours": 732
  },
  "stats": {
   "total_events": 131,
   "stars": 298,
   "forks": 3,
   "pushes": 4,
   "issues": 1,
   "pull_requests": 0,
   "other": 2,
   "unique_actors": 97,
   "star_actors": 94,
   "peak_stars_10m": 31,
   "stars_by_hour": []
  },
  "score": {
   "score": 64,
   "level": "high",
   "verdict": "Заметные признаки накрутки: главный сигнал — «свежие и пустые аккаунты».",
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
     "summary": "72 из 298 звёзд — от аккаунтов моложе 30 дн. или без активности (24%).",
     "score": 0.9,
     "confidence": 0.85,
     "weight": 0.22,
     "contribution": 0.198,
     "active": true,
     "error": null,
     "details": {
      "mocked": false
     }
    },
    {
     "id": "engagement",
     "title": "Звёзды без активности",
     "summary": "На 298 звёзд — 14 действий (форки, issues, PR, пуши).",
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
     "summary": "Нет индекса звёзд по другим репозиториям — проверка не участвует в оценке.",
     "score": 0,
     "confidence": 0,
     "weight": 0.22,
     "contribution": 0,
     "active": false,
     "error": null,
     "details": {
      "mocked": false,
      "reason": "no_cross_repo_index"
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
  "source": {
   "type": "github_api",
   "stars_sampled": 298,
   "stars_total": 1840,
   "coverage_from": "2026-10-12T12:00:00Z"
  },
  "stars_timeline": {
   "bucket_minutes": 720,
   "estimated": false,
   "suspicious_stars": 72,
   "suspicious_by_check": {
    "fresh_accounts": 72
   },
   "points": [
    {
     "at": "2026-10-12T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-13T00:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-13T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-14T00:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-14T12:00:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-10-15T00:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-15T12:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-10-16T00:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-10-16T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-17T00:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-10-17T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-18T00:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-10-18T12:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-10-19T00:00:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-10-19T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-20T00:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-20T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-21T00:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-10-21T12:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-10-22T00:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-22T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-23T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-10-23T12:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-10-24T00:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-10-24T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-25T00:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-25T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-10-26T00:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-26T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-27T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-10-27T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-10-28T00:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-10-28T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-10-29T00:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-10-29T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-10-30T00:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-10-30T12:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-10-31T00:00:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-10-31T12:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-01T00:00:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-01T12:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-02T00:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-02T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-03T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-03T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-04T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-04T12:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-05T00:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-05T12:00:00Z",
     "stars": 5,
     "suspicious": 0
    },
    {
     "at": "2026-11-06T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-06T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-07T00:00:00Z",
     "stars": 3,
     "suspicious": 0
    },
    {
     "at": "2026-11-07T12:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-08T00:00:00Z",
     "stars": 1,
     "suspicious": 0
    },
    {
     "at": "2026-11-08T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-09T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-09T12:00:00Z",
     "stars": 2,
     "suspicious": 0
    },
    {
     "at": "2026-11-10T00:00:00Z",
     "stars": 75,
     "suspicious": 72
    },
    {
     "at": "2026-11-10T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    },
    {
     "at": "2026-11-11T00:00:00Z",
     "stars": 4,
     "suspicious": 0
    },
    {
     "at": "2026-11-11T12:00:00Z",
     "stars": 6,
     "suspicious": 0
    }
   ]
  }
 }
};
