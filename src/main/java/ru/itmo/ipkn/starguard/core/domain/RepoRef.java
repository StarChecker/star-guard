package ru.itmo.ipkn.starguard.core.domain;

/** Идентификатор репозитория GitHub: owner/name. */
public record RepoRef(String owner, String name) {

    public RepoRef {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    public String fullName() {
        return owner + "/" + name;
    }
}
