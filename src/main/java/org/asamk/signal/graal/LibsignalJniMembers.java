package org.asamk.signal.graal;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Selects what libsignal's native library reaches through JNI by name: a class it looks up, or a
 * method, constructor or field it calls or reads.
 * <p>
 * libsignal marks these with {@code @CalledFromNative} and describes the rest in the ProGuard rules it
 * ships (META-INF/proguard/libsignal.pro), which keep them from being removed on Android. A native
 * image removes them in the same way unless they are registered, so the selection follows those rules.
 */
public final class LibsignalJniMembers {

    public static final String PACKAGE = "org.signal.libsignal.";

    private static final String CALLED_FROM_NATIVE = "org.signal.libsignal.internal.CalledFromNative";

    private LibsignalJniMembers() {
    }

    /**
     * The members of a libsignal class that native code may access. A class with any such member, or
     * marked itself, has to be findable by name as well: see {@link #isFoundByName(Class)}.
     */
    public static List<Member> of(final Class<?> clazz) {
        final var members = new ArrayList<Member>();
        final var classIsMarked = isMarked(clazz);

        for (final var method : clazz.getDeclaredMethods()) {
            // Native code calls the methods of a marked interface on objects of other classes.
            if (isMarked(method) || (classIsMarked && clazz.isInterface())) {
                members.add(method);
            }
        }
        for (final var constructor : clazz.getDeclaredConstructors()) {
            final var parameters = constructor.getParameterTypes();
            // Native code builds exceptions to throw, and wraps a pointer it owns with the constructor
            // taking that pointer.
            if (isMarked(constructor) || Throwable.class.isAssignableFrom(clazz) || (
                    parameters.length == 1 && parameters[0] == long.class
            )) {
                members.add(constructor);
            }
        }
        for (final var field : clazz.getDeclaredFields()) {
            // Native code reads the values of a marked enum.
            if (isMarked(field) || (classIsMarked && field.isEnumConstant())) {
                members.add(field);
            }
        }
        return members;
    }

    public static boolean isFoundByName(final Class<?> clazz) {
        return isMarked(clazz) || !of(clazz).isEmpty();
    }

    private static boolean isMarked(final Class<?> clazz) {
        return isMarked(clazz.getDeclaredAnnotations());
    }

    private static boolean isMarked(final AccessibleObject member) {
        return isMarked(member.getDeclaredAnnotations());
    }

    private static boolean isMarked(final Annotation[] annotations) {
        return Arrays.stream(annotations).anyMatch(a -> a.annotationType().getName().equals(CALLED_FROM_NATIVE));
    }
}
