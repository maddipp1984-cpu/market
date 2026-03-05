package de.projekt.timeseries.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectTypeTest {

    @Test
    void fromCode_allTypes() {
        assertEquals(ObjectType.CONTRACT_VHP, ObjectType.fromCode(1));
        assertEquals(ObjectType.CONTRACT, ObjectType.fromCode(2));
        assertEquals(ObjectType.CONTRACT_VERANS, ObjectType.fromCode(3));
        assertEquals(ObjectType.ANS, ObjectType.fromCode(4));
    }

    @Test
    void fromCode_unknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> ObjectType.fromCode(99));
    }

    @Test
    void descriptionNotNull() {
        for (ObjectType t : ObjectType.values()) {
            assertNotNull(t.getDescription());
            assertFalse(t.getDescription().isEmpty());
        }
    }
}
