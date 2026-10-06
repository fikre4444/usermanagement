package com.usermanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.usermanagement.common.error.ApiException;
import org.junit.jupiter.api.Test;

class IdentifiersTest {

    @Test
    void normalisesIdentifiers() {
        assertThat(Identifiers.email("  John.Doe@Example.COM ")).isEqualTo("john.doe@example.com");
        assertThat(Identifiers.phone("+251 (91) 123-4567")).isEqualTo("+251911234567");
        assertThat(Identifiers.username("John_Doe")).isEqualTo("john_doe");
        assertThat(Identifiers.email("  ")).isNull();
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThatThrownBy(() -> Identifiers.email("not-an-email")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Identifiers.phone("0911234567")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Identifiers.username("a!")).isInstanceOf(ApiException.class);
    }

    @Test
    void detectsIdentifierKind() {
        assertThat(Identifiers.kindOf("a@b.co")).isEqualTo(Identifiers.Kind.EMAIL);
        assertThat(Identifiers.kindOf("+251911234567")).isEqualTo(Identifiers.Kind.PHONE);
        assertThat(Identifiers.kindOf("john")).isEqualTo(Identifiers.Kind.USERNAME);
    }
}
