package de.market.commoditygroup.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ts_commodity_group")
public class CommodityGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commodity_group_id")
    private Short id;

    @Column(name = "name", nullable = false, unique = true, columnDefinition = "TEXT")
    private String name;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
