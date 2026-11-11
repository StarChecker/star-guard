package ru.itmo.ipkn.starguard.core.archive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Строки NDJSON для сверки потокового парсера с прежним: форма реальных событий
 * GH Archive, ловушки во вложенном payload и пограничные случаи валидации.
 */
final class EquivalenceCorpus {

    private static final String ACTOR = """
            {"id":88813507,"login":"dev","display_login":"dev","gravatar_id":"",\
            "url":"https://api.github.com/users/dev","avatar_url":"https://avatars.githubusercontent.com/u/1?"}""";
    private static final String REPO = """
            {"id":753101648,"name":"demo/star-guard","url":"https://api.github.com/repos/demo/star-guard"}""";
    private static final String CREATED = "\"2026-11-10T12:00:01Z\"";

    private EquivalenceCorpus() {
    }

    static List<byte[]> lines() {
        List<String> lines = new ArrayList<>();

        // Реальная форма: id, type, actor, repo, payload, public, created_at, org.
        lines.add(event("1", "WatchEvent", ACTOR, REPO, "{\"action\":\"started\"}")
                + "");
        lines.add(withOrg(event("2", "WatchEvent", ACTOR, REPO, "{\"action\":\"started\"}")));
        for (String type : List.of("ForkEvent", "PushEvent", "IssuesEvent", "PullRequestEvent",
                "IssueCommentEvent", "ReleaseEvent", "PullRequestReviewEvent", "GollumEvent")) {
            lines.add(event("3" + type.length(), type, ACTOR, REPO, "{\"action\":\"opened\",\"number\":7}"));
        }

        // В payload — вложенные поля с теми же именами: верхний уровень не должен перетираться.
        lines.add(event("10", "PullRequestEvent", ACTOR, REPO, """
                {"action":"opened","number":1,"pull_request":{"id":999,"type":"Bot",\
                "user":{"id":1,"login":"decoy","created_at":"2001-01-01T00:00:00Z"},\
                "head":{"repo":{"id":5,"name":"evil/decoy"}},"created_at":"2001-01-01T00:00:00Z",\
                "labels":[{"id":1,"name":"bug"},{"id":2,"name":"x"}],"draft":false,"merged_at":null}}"""));
        lines.add(event("11", "IssueCommentEvent", ACTOR, REPO, """
                {"comment":{"body":"line1\\nline2 \\"quoted\\" \\\\ backslash \\u00e9 \\ud83d\\ude00",\
                "raw":"Привет 🌍 — ünïcödé","nested":[[[[{"actor":{"id":2}}]]]]},"empty":{},"arr":[]}"""));
        lines.add(event("12", "PushEvent", ACTOR, REPO, deepPayload(200)));
        lines.add(event("13", "ReleaseEvent", ACTOR, REPO, "null"));
        lines.add(event("14", "ReleaseEvent", ACTOR, REPO, "[1,2.5,-3e10,true,false,null,{\"a\":[]}]"));

        // Другой порядок полей: repo раньше type, created_at первым, actor последним.
        lines.add("{\"created_at\":" + CREATED + ",\"repo\":" + REPO + ",\"type\":\"PullRequestEvent\","
                + "\"payload\":{},\"id\":\"20\",\"actor\":" + ACTOR + "}");
        lines.add("{\"repo\":{\"id\":1,\"name\":\"A/B\"},\"type\":\"WatchEvent\",\"id\":\"21\","
                + "\"actor\":{\"id\":3},\"created_at\":" + CREATED + "}");
        lines.add("{\"repo\":{\"id\":1,\"name\":\"a\\/b\"},\"type\":\"PushEvent\",\"id\":\"22\","
                + "\"actor\":{\"id\":3},\"created_at\":" + CREATED + "}");
        lines.add(" \t{ \"id\" : \"23\" , \"type\" : \"WatchEvent\" , \"actor\" : { \"id\" : 4 } , "
                + "\"repo\" : { \"id\" : 2 , \"name\" : \"a/b\" } , \"created_at\" : " + CREATED + " } ");

        // Типы значений: число вместо строки и наоборот.
        lines.add(event("30", "WatchEvent", ACTOR, "{\"id\":\"42\",\"name\":\"a/b\"}", "{}"));
        lines.add(event("31", "WatchEvent", "{\"id\":\"7\",\"login\":\"x\"}", REPO, "{}"));
        lines.add(event("32", "WatchEvent", ACTOR, "{\"id\":42.0,\"name\":\"a/b\"}", "{}"));
        lines.add(event("33", "WatchEvent", ACTOR, "{\"id\":-5,\"name\":\"a/b\"}", "{}"));
        lines.add(event("34", "WatchEvent", ACTOR, "{\"id\":\"abc\",\"name\":\"a/b\"}", "{}"));
        lines.add(event("35", "WatchEvent", ACTOR, "{\"id\":null,\"name\":\"a/b\"}", "{}"));
        lines.add(event("36", "WatchEvent", ACTOR, "{\"id\":{\"x\":1},\"name\":\"a/b\"}", "{}"));
        lines.add(event("37", "WatchEvent", ACTOR, "{\"id\":[1],\"name\":\"a/b\"}", "{}"));
        lines.add(event("38", "WatchEvent", ACTOR, "{\"id\":1,\"name\":123}", "{}"));
        lines.add(event("39", "WatchEvent", ACTOR, "{\"id\":1,\"name\":\"\"}", "{}"));
        lines.add(event("40", "WatchEvent", ACTOR, "{\"id\":1,\"name\":\"   \"}", "{}"));
        lines.add(event("41", "WatchEvent", ACTOR, "{\"id\":1,\"name\":null}", "{}"));
        lines.add(event("42", "WatchEvent", ACTOR, "{\"id\":1,\"name\":{\"a\":1}}", "{}"));
        lines.add(event("43", "WatchEvent", ACTOR, "{\"id\":0,\"name\":\"a/b\"}", "{}"));
        lines.add(event("44", "WatchEvent", "{\"id\":0,\"login\":\"x\"}", REPO, "{}"));
        lines.add(event("45", "WatchEvent", "{\"id\":7}", REPO, "{}"));
        lines.add(event("46", "WatchEvent", "{\"id\":7,\"login\":null}", REPO, "{}"));
        lines.add(event("47", "WatchEvent", "{\"id\":7,\"login\":\"\"}", REPO, "{}"));
        lines.add(event("48", "WatchEvent", "{\"id\":7,\"created_at\":\"2026-11-01T08:00:00Z\"}", REPO, "{}"));
        lines.add(event("49", "WatchEvent", "{\"id\":7,\"created_at\":\"yesterday\"}", REPO, "{}"));
        lines.add(event("50", "WatchEvent", "\"actor\"", REPO, "{}"));
        lines.add(event("51", "WatchEvent", "null", REPO, "{}"));
        lines.add(event("52", "WatchEvent", ACTOR, "null", "{}"));
        lines.add(event("53", "WatchEvent", ACTOR, "[\"a/b\"]", "{}"));
        lines.add(event("54", "WatchEvent", ACTOR, "\"demo/star-guard\"", "{}"));
        lines.add("{\"id\":55,\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"56\",\"type\":123,\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"57\",\"type\":\"\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"58\",\"type\":null,\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"59\",\"type\":\"watchevent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":null,\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":{\"a\":1},\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":true,\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":" + CREATED + "}");

        // created_at: отсутствует, null, не дата, другой формат ISO.
        lines.add("{\"id\":\"60\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO + "}");
        lines.add("{\"id\":\"61\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":null}");
        lines.add("{\"id\":\"62\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":\"10.11.2026\"}");
        lines.add("{\"id\":\"63\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":\"2026-11-10T12:00:01.123Z\"}");
        lines.add("{\"id\":\"64\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"created_at\":1731240001}");

        // Отсутствующие блоки и не-объекты в корне.
        lines.add("{\"id\":\"70\",\"type\":\"WatchEvent\",\"repo\":" + REPO + ",\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"71\",\"type\":\"WatchEvent\",\"actor\":" + ACTOR + ",\"created_at\":" + CREATED + "}");
        lines.add("{}");
        lines.add("[]");
        lines.add("[{\"id\":\"1\"}]");
        lines.add("\"text\"");
        lines.add("42");
        lines.add("null");
        lines.add("true");

        // Синтаксически битые строки.
        lines.add("{not-json");
        lines.add("{\"id\":\"80\",\"type\":\"WatchEvent\",\"actor\":{\"id\":1");
        lines.add("{\"id\":\"81\",}");
        lines.add("{'id':'82'}");
        lines.add("{\"id\":\"83\" \"type\":\"WatchEvent\"}");
        lines.add("{\"id\":\"84\",\"type\":\"PushEvent\",\"actor\":" + ACTOR + ",\"repo\":" + REPO
                + ",\"payload\":{\"broken\":[1,2,}");
        lines.add("{\"id\":\"85\",\"type\":\"PushEvent\",\"actor\":" + ACTOR + ",\"repo\":{\"id\":9,"
                + "\"name\":\"other/repo\"},\"payload\":{\"broken\":tru},\"created_at\":" + CREATED + "}");
        lines.add("{\"id\":\"86\",\"type\":\"WatchEvent\",\"payload\":\"bad \\x escape\"}");

        List<byte[]> bytes = new ArrayList<>(lines.size());
        for (String line : lines) {
            bytes.add(line.getBytes(StandardCharsets.UTF_8));
        }
        return bytes;
    }

    private static String event(String id, String type, String actor, String repo, String payload) {
        return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"actor\":" + actor + ",\"repo\":" + repo
                + ",\"payload\":" + payload + ",\"public\":true,\"created_at\":" + CREATED + "}";
    }

    private static String withOrg(String event) {
        return event.substring(0, event.length() - 1)
                + ",\"org\":{\"id\":1,\"login\":\"org\",\"url\":\"https://api.github.com/orgs/org\"}}";
    }

    private static String deepPayload(int depth) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append(i % 2 == 0 ? "{\"repo\":" : "[");
        }
        builder.append("{\"id\":1,\"name\":\"evil/deep\"}");
        for (int i = depth - 1; i >= 0; i--) {
            builder.append(i % 2 == 0 ? "}" : "]");
        }
        return builder.toString();
    }
}
