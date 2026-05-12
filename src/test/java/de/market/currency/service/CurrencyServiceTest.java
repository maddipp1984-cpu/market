package de.market.currency.service;

import de.market.currency.model.CurrencyEntity;
import de.market.currency.repository.CurrencyJpaRepository;
import de.market.currency.repository.CurrencyOverviewRepository;
import de.market.currency.rest.dto.CurrencyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock private CurrencyJpaRepository repository;
    @Mock private CurrencyOverviewRepository overviewRepository;

    private CurrencyService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyService(repository, overviewRepository);
    }

    // ---- findAllAsRows / findFiltered ----

    @Test
    void findAllAsRowsShouldDelegateToOverviewRepository() {
        List<Map<String, Object>> rows = List.of(Map.of("isoCode", "EUR"));
        when(overviewRepository.findAllAsRows()).thenReturn(rows);

        assertThat(service.findAllAsRows()).isSameAs(rows);
    }

    @Test
    void findFilteredShouldDelegateToOverviewRepository() {
        List<Map<String, Object>> rows = List.of();
        when(overviewRepository.findFiltered(any())).thenReturn(rows);

        assertThat(service.findFiltered(null)).isSameAs(rows);
    }

    // ---- findById ----

    @Test
    void findByIdShouldReturnDto() {
        CurrencyEntity entity = entity((short) 1, "EUR", "Euro");
        when(repository.findById((short) 1)).thenReturn(Optional.of(entity));

        CurrencyDto result = service.findById((short) 1);
        assertThat(result.getIsoCode()).isEqualTo("EUR");
        assertThat(result.getDescription()).isEqualTo("Euro");
    }

    @Test
    void findByIdShouldThrowWhenNotFound() {
        when(repository.findById((short) 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById((short) 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Waehrung nicht gefunden");
    }

    // ---- create ----

    @Test
    void createShouldSaveAndReturnDto() {
        CurrencyDto dto = dto(null, "EUR", "Euro");
        CurrencyEntity saved = entity((short) 1, "EUR", "Euro");
        when(repository.existsByIsoCode("EUR")).thenReturn(false);
        when(repository.save(any())).thenReturn(saved);

        CurrencyDto result = service.create(dto);
        assertThat(result.getId()).isEqualTo((short) 1);
        assertThat(result.getIsoCode()).isEqualTo("EUR");

        ArgumentCaptor<CurrencyEntity> captor = ArgumentCaptor.forClass(CurrencyEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void createShouldThrowWhenIsoCodeBlank() {
        assertThatThrownBy(() -> service.create(dto(null, "", "Name")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pflichtfeld");
    }

    @Test
    void createShouldThrowWhenDuplicateIsoCode() {
        CurrencyDto dto = dto(null, "EUR", "Euro");
        when(repository.existsByIsoCode("EUR")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits vergeben");
        verify(repository, never()).save(any());
    }

    // ---- update ----

    @Test
    void updateShouldModifyAndSave() {
        CurrencyEntity existing = entity((short) 1, "EUR", "Euro");
        when(repository.findById((short) 1)).thenReturn(Optional.of(existing));
        when(repository.existsByIsoCodeAndIdNot("USD", (short) 1)).thenReturn(false);
        when(repository.save(any())).thenReturn(existing);

        CurrencyDto result = service.update((short) 1, dto((short) 1, "USD", "Euro Updated"));
        assertThat(result.getIsoCode()).isEqualTo("USD");
        verify(repository).save(existing);
    }

    @Test
    void updateShouldThrowWhenNotFound() {
        when(repository.findById((short) 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update((short) 99, dto((short) 99, "EUR", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nicht gefunden");
    }

    @Test
    void updateShouldThrowWhenDuplicateIsoCode() {
        CurrencyEntity existing = entity((short) 1, "EUR", "Euro");
        when(repository.findById((short) 1)).thenReturn(Optional.of(existing));
        when(repository.existsByIsoCodeAndIdNot("USD", (short) 1)).thenReturn(true);

        assertThatThrownBy(() -> service.update((short) 1, dto((short) 1, "USD", "X")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bereits vergeben");
        verify(repository, never()).save(any());
    }

    // ---- delete ----

    @Test
    void deleteShouldCallRepository() {
        when(repository.existsById((short) 1)).thenReturn(true);

        service.delete((short) 1);
        verify(repository).deleteById((short) 1);
        verify(repository).flush();
    }

    @Test
    void deleteShouldThrowWhenNotFound() {
        when(repository.existsById((short) 99)).thenReturn(false);

        assertThatThrownBy(() -> service.delete((short) 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nicht gefunden");
    }

    @Test
    void deleteShouldThrowIllegalStateOnFkConstraint() {
        when(repository.existsById((short) 1)).thenReturn(true);
        doThrow(new RuntimeException("FK violation")).when(repository).deleteById((short) 1);

        assertThatThrownBy(() -> service.delete((short) 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("referenziert");
    }

    // ---- helpers ----

    private CurrencyEntity entity(Short id, String isoCode, String description) {
        CurrencyEntity e = new CurrencyEntity();
        e.setId(id);
        e.setIsoCode(isoCode);
        e.setDescription(description);
        return e;
    }

    private CurrencyDto dto(Short id, String isoCode, String description) {
        CurrencyDto d = new CurrencyDto();
        d.setId(id);
        d.setIsoCode(isoCode);
        d.setDescription(description);
        return d;
    }
}
