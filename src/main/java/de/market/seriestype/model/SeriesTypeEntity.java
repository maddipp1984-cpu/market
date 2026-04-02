package de.market.seriestype.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ts_series_type")
public class SeriesTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "series_type_id")
    private Short id;

    @Column(name = "code", nullable = false, unique = true, columnDefinition = "TEXT")
    private String code;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(name = "category", nullable = false)
    private Short category;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Short getCategory() { return category; }
    public void setCategory(Short category) { this.category = category; }
}
