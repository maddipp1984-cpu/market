package de.market.shared.service;

/**
 * Basisklasse für Stammdaten-Services mit Pflicht-Validierung.
 *
 * @param <D> DTO-Typ
 * @param <E> Entity-Typ
 * @param <ID> ID-Typ (Long, Short, Integer, ...)
 */
public abstract class AbstractCrudService<D, E, ID> {

    /**
     * Validiert das DTO vor create/update.
     * Wirft IllegalArgumentException bei ungültigen Daten.
     */
    protected abstract void validate(D dto);

    protected abstract D toDto(E entity);

    protected abstract E toEntity(D dto);
}
