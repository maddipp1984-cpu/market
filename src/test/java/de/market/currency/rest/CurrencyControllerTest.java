package de.market.currency.rest;

import de.market.currency.rest.dto.CurrencyDto;
import de.market.currency.service.CurrencyService;
import de.market.shared.dto.FilterCondition;
import de.market.shared.dto.FilterRequest;
import de.market.shared.dto.TableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyControllerTest {

    @Mock private CurrencyService service;

    private CurrencyController controller;

    @BeforeEach
    void setUp() {
        controller = new CurrencyController(service);
    }

    @Test
    void getAllShouldReturnTableResponse() {
        when(service.findAllAsRows()).thenReturn(List.of(Map.of("isoCode", "EUR")));

        ResponseEntity<TableResponse> response = controller.getAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getColumns()).hasSize(3);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    void queryShouldReturnFiltered() {
        when(service.findFiltered(any())).thenReturn(List.of(Map.of("isoCode", "USD")));

        FilterCondition fc = new FilterCondition();
        fc.setSqlColumn("iso_code");
        fc.setOperator("=");
        fc.setValue("USD");
        FilterRequest req = new FilterRequest();
        req.setConditions(List.of(fc));
        ResponseEntity<TableResponse> response = controller.query(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().get(0)).containsEntry("isoCode", "USD");
    }

    @Test
    void queryWithEmptyConditionsShouldReturnAll() {
        when(service.findAllAsRows()).thenReturn(List.of());

        ResponseEntity<TableResponse> response = controller.query(new FilterRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getByIdShouldReturnDto() {
        CurrencyDto dto = dto((short) 1, "EUR", "Euro");
        when(service.findById((short) 1)).thenReturn(dto);

        ResponseEntity<CurrencyDto> response = controller.getById((short) 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getIsoCode()).isEqualTo("EUR");
    }

    @Test
    void createShouldReturn201() {
        CurrencyDto created = dto((short) 1, "GBP", "Pfund");
        when(service.create(any())).thenReturn(created);

        ResponseEntity<CurrencyDto> response = controller.create(dto(null, "GBP", "Pfund"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo((short) 1);
    }

    @Test
    void updateShouldReturn200() {
        CurrencyDto updated = dto((short) 1, "EUR", "Euro Updated");
        when(service.update(eq((short) 1), any())).thenReturn(updated);

        ResponseEntity<CurrencyDto> response = controller.update((short) 1, dto((short) 1, "EUR", "Euro Updated"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDescription()).isEqualTo("Euro Updated");
    }

    @Test
    void deleteShouldReturn204() {
        ResponseEntity<Void> response = controller.delete((short) 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete((short) 1);
    }

    // ---- helper ----

    private CurrencyDto dto(Short id, String isoCode, String description) {
        CurrencyDto d = new CurrencyDto();
        d.setId(id);
        d.setIsoCode(isoCode);
        d.setDescription(description);
        return d;
    }
}
