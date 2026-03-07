package de.projekt.businesspartner.rest.dto;

import java.util.List;

public class BusinessPartnerDto {
    private Long id;
    private String shortName;
    private String name;
    private String notes;
    private List<ContactPersonDto> contacts;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<ContactPersonDto> getContacts() { return contacts; }
    public void setContacts(List<ContactPersonDto> contacts) { this.contacts = contacts; }
}
