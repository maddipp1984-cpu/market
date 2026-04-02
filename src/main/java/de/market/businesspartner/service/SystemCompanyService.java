package de.market.businesspartner.service;

import de.market.businesspartner.model.SystemCompanyEntry;
import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static de.market.jooq.generated.tables.BusinessPartner.BUSINESS_PARTNER;

@Service
public class SystemCompanyService {

    private static final Logger log = LoggerFactory.getLogger(SystemCompanyService.class);

    private final DSLContext dsl;
    private volatile List<SystemCompanyEntry> cache = List.of();

    public SystemCompanyService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @PostConstruct
    void loadOnStartup() {
        reload();
    }

    public void reload() {
        cache = dsl
                .select(
                        BUSINESS_PARTNER.ID,
                        BUSINESS_PARTNER.SHORT_NAME,
                        BUSINESS_PARTNER.NAME,
                        BUSINESS_PARTNER.SYSTEM_RANK
                )
                .from(BUSINESS_PARTNER)
                .where(BUSINESS_PARTNER.SYSTEM_RANK.isNotNull())
                .orderBy(BUSINESS_PARTNER.SYSTEM_RANK)
                .fetch(r -> new SystemCompanyEntry(
                        r.get(BUSINESS_PARTNER.ID),
                        r.get(BUSINESS_PARTNER.SHORT_NAME),
                        r.get(BUSINESS_PARTNER.NAME),
                        r.get(BUSINESS_PARTNER.SYSTEM_RANK)
                ));
        log.info("Systemfirmen geladen: {} Eintraege", cache.size());
        getPrimary().ifPresent(p ->
                log.info("Fuehrende Systemfirma: {} ({})", p.shortName(), p.name()));
    }

    public Optional<SystemCompanyEntry> getPrimary() {
        return cache.stream().filter(e -> e.rank() == 1).findFirst();
    }

    public List<SystemCompanyEntry> getAll() {
        return cache;
    }

    public boolean isSystemCompany(Long partnerId) {
        return cache.stream().anyMatch(e -> e.partnerId().equals(partnerId));
    }
}
