package de.market.publicapi.counterpart;

import de.market.businesspartner.rest.dto.BusinessPartnerDto;
import de.market.businesspartner.service.BusinessPartnerService;
import de.market.publicapi.counterpart.dto.CounterpartResponse;
import de.market.publicapi.counterpart.dto.CreateCounterpartRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public-api/counterparts")
public class CounterpartController {

    private final BusinessPartnerService service;

    public CounterpartController(BusinessPartnerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CounterpartResponse> create(@RequestBody CreateCounterpartRequest request) {
        BusinessPartnerDto dto = new BusinessPartnerDto();
        dto.setShortName(request.getShortName());
        dto.setName(request.getName());

        BusinessPartnerDto created = service.create(dto);

        CounterpartResponse response = new CounterpartResponse(
                created.getId(),
                created.getShortName(),
                created.getName()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
