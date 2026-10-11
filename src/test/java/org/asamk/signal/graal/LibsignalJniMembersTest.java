package org.asamk.signal.graal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the selection against libsignal classes whose use from native code is known: each stands
 * for one way the native library reaches into Java.
 */
class LibsignalJniMembersTest {

    private static List<Member> membersOf(String className) throws ClassNotFoundException {
        return LibsignalJniMembers.of(Class.forName(className));
    }

    @Test
    void selectsTheMethodThatConvertsANativeResult() throws ClassNotFoundException {
        // The class whose absence from the native image made listDevices panic.
        final var members = membersOf("org.signal.libsignal.internal.LinkedDeviceInternal_ReturnConverter");
        assertTrue(members.stream().anyMatch(m -> m instanceof Method && m.getName().equals("fromNative")));
    }

    @Test
    void selectsTheConstructorsOfAnExceptionNativeCodeThrows() throws ClassNotFoundException {
        final var members = membersOf("org.signal.libsignal.net.DeviceIdNotFoundException");
        assertTrue(members.stream().anyMatch(m -> m instanceof Constructor));
    }

    @Test
    void selectsTheConstructorThatWrapsANativePointer() throws ClassNotFoundException {
        final var members = membersOf("org.signal.libsignal.protocol.ecc.ECPublicKey");
        assertTrue(members.stream()
                .anyMatch(m -> m instanceof Constructor<?> c
                        && c.getParameterCount() == 1
                        && c.getParameterTypes()[0] == long.class));
    }

    @Test
    void selectsNothingFromAClassNativeCodeDoesNotTouch() throws ClassNotFoundException {
        final var clazz = Class.forName("org.signal.libsignal.protocol.util.Hex");
        assertTrue(LibsignalJniMembers.of(clazz).isEmpty());
        assertFalse(LibsignalJniMembers.isFoundByName(clazz));
    }
}
