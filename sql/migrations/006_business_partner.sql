-- Geschaeftspartner
CREATE TABLE business_partner (
    id          BIGSERIAL PRIMARY KEY,
    short_name  VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    notes       TEXT
);

-- Ansprechpartner
CREATE TABLE contact_person (
    id          BIGSERIAL PRIMARY KEY,
    partner_id  BIGINT       NOT NULL REFERENCES business_partner(id) ON DELETE CASCADE,
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255),
    phone       VARCHAR(50),
    street      VARCHAR(255),
    zip_code    VARCHAR(10),
    city        VARCHAR(100)
);

CREATE INDEX idx_contact_person_partner ON contact_person(partner_id);

-- Funktionen (Mehrfachauswahl pro Ansprechpartner)
CREATE TABLE contact_person_function (
    contact_person_id  BIGINT      NOT NULL REFERENCES contact_person(id) ON DELETE CASCADE,
    function_type      VARCHAR(30) NOT NULL,
    PRIMARY KEY (contact_person_id, function_type)
);
