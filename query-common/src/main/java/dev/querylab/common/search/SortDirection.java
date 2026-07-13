package dev.querylab.common.search;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return DESC;
        }
        return switch (raw.trim().toLowerCase()) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new InvalidCriteriaException(
                    "Unknown sort direction: '" + raw + "'. Allowed: asc, desc");
        };
    }
}
