package de.market.shared.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;

import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.noCondition;

/**
 * Basisklasse fuer Uebersichts-Repositories (Tabellen-Ansichten).
 * Jedes Uebersichts-Repository muss findAllAsRows() und findFiltered() implementieren.
 */
public abstract class AbstractOverviewRepository {

    protected final DSLContext dsl;

    protected AbstractOverviewRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Liefert alle Zeilen fuer die Uebersichtstabelle.
     */
    public abstract List<Map<String, Object>> findAllAsRows();

    /**
     * Liefert gefilterte Zeilen basierend auf einer jOOQ Condition.
     */
    public abstract List<Map<String, Object>> findFiltered(Condition condition);
}
