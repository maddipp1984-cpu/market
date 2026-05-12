package de.market.shared.service;

import de.market.shared.repository.AbstractOverviewRepository;
import org.jooq.Condition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Basisklasse fuer Stammdaten-Services mit gemeinsamer CRUD-Logik.
 * Subklassen muessen nur noch domainspezifische Aspekte bereitstellen:
 * Validierung, Mapping, Unique-Checks, Feld-Kopie.
 *
 * @param <D> DTO-Typ
 * @param <E> Entity-Typ
 * @param <ID> ID-Typ (Short, Long, ...)
 */
public abstract class AbstractCrudService<D, E, ID> {

    protected abstract JpaRepository<E, ID> getRepository();

    protected abstract AbstractOverviewRepository getOverviewRepository();

    /** Lesbarer Name der Entitaet fuer Fehlermeldungen (z.B. "Waehrung"). */
    protected abstract String getEntityName();

    protected abstract void validate(D dto);

    protected abstract D toDto(E entity);

    protected abstract E toEntity(D dto);

    /** Setzt die ID auf null und fuehrt ggf. weitere Vorbereitungen vor dem Speichern durch. */
    protected abstract void prepareForCreate(E entity);

    /** Prueft domainspezifische Unique-Constraints vor dem Anlegen. Wirft IllegalStateException bei Konflikt. */
    protected abstract void checkUniqueOnCreate(D dto);

    /** Prueft domainspezifische Unique-Constraints vor dem Update (mit ID-Ausnahme). */
    protected abstract void checkUniqueOnUpdate(D dto, ID id);

    /** Kopiert alle relevanten Felder vom DTO auf die existierende Entity. */
    protected abstract void copyFieldsForUpdate(E existing, D dto);

    // ---- Konkrete CRUD-Methoden ----

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllAsRows() {
        return getOverviewRepository().findAllAsRows();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findFiltered(Condition condition) {
        return getOverviewRepository().findFiltered(condition);
    }

    @Transactional(readOnly = true)
    public D findById(ID id) {
        E entity = getRepository().findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        getEntityName() + " nicht gefunden: id=" + id));
        return toDto(entity);
    }

    @Transactional
    public D create(D dto) {
        validate(dto);
        checkUniqueOnCreate(dto);
        E entity = toEntity(dto);
        prepareForCreate(entity);
        return toDto(getRepository().save(entity));
    }

    @Transactional
    public D update(ID id, D dto) {
        validate(dto);
        E existing = getRepository().findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        getEntityName() + " nicht gefunden: id=" + id));
        checkUniqueOnUpdate(dto, id);
        copyFieldsForUpdate(existing, dto);
        return toDto(getRepository().save(existing));
    }

    @Transactional
    public void delete(ID id) {
        if (!getRepository().existsById(id)) {
            throw new IllegalArgumentException(getEntityName() + " nicht gefunden: id=" + id);
        }
        try {
            getRepository().deleteById(id);
            getRepository().flush();
        } catch (Exception e) {
            throw new IllegalStateException(
                    getEntityName() + " wird noch referenziert und kann nicht geloescht werden");
        }
    }
}
