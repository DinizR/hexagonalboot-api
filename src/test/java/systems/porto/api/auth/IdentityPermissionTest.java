package systems.porto.api.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityPermissionTest {

    @Test
    void globalWildcardMatchesEveryCode() {
        Identity identity = identity(Set.of("*", "cash-flow:read"));
        assertEquals(Set.of("*"), identity.permissions());
        assertTrue(identity.hasPermission("cash-flow:settle"));
        assertTrue(identity.hasPermission("city:list"));
        assertTrue(identity.hasPermission("*"));
    }

    @Test
    void entityWildcardMatchesActionsOnThatEntityOnly() {
        Identity identity = identity(Set.of("cash-flow:*", "cash-flow:read", "city:list"));
        assertEquals(Set.of("cash-flow:*", "city:list"), identity.permissions());
        assertTrue(identity.hasPermission("cash-flow:read"));
        assertTrue(identity.hasPermission("cash-flow:settle"));
        assertTrue(identity.hasPermission("city:list"));
        assertFalse(identity.hasPermission("city:delete"));
        assertFalse(identity.hasPermission("*"));
    }

    @Test
    void leafCodeMatchesOnlyItself() {
        Identity identity = identity(Set.of("cash-flow:read"));
        assertTrue(identity.hasPermission("cash-flow:read"));
        assertFalse(identity.hasPermission("cash-flow:update"));
        assertFalse(identity.hasPermission("cash-flow:*"));
    }

    private static Identity identity(final Set<String> permissions) {
        return new Identity("1", "admin", "admin@example.com", Set.of("ADMIN"), permissions, java.util.Map.of());
    }
}
