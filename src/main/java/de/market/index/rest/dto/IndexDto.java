package de.market.index.rest.dto;

public class IndexDto {
    private Long id;
    private String name;
    private String description;
    private Integer timeDim;
    private Short unitId;
    private Short currencyId;
    private Long tsId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getTimeDim() { return timeDim; }
    public void setTimeDim(Integer timeDim) { this.timeDim = timeDim; }

    public Short getUnitId() { return unitId; }
    public void setUnitId(Short unitId) { this.unitId = unitId; }

    public Short getCurrencyId() { return currencyId; }
    public void setCurrencyId(Short currencyId) { this.currencyId = currencyId; }

    public Long getTsId() { return tsId; }
    public void setTsId(Long tsId) { this.tsId = tsId; }
}
