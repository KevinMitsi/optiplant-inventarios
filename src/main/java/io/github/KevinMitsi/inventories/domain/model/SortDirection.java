package io.github.KevinMitsi.inventories.domain.model;

/** Sentido de ordenación de una consulta paginada. */
public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection fromString(String value) {
        return value != null && value.equalsIgnoreCase("desc") ? DESC : ASC;
    }

    public boolean isDescending() {
        return this == DESC;
    }
}
