package de.market.businesspartner.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contact_person")
public class ContactPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String street;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(length = 100)
    private String city;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "contact_person_function",
        joinColumns = @JoinColumn(name = "contact_person_id")
    )
    @Column(name = "function_type")
    @Enumerated(EnumType.STRING)
    private Set<ContactFunction> functions = new HashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Set<ContactFunction> getFunctions() { return functions; }
    public void setFunctions(Set<ContactFunction> functions) { this.functions = functions; }
}
