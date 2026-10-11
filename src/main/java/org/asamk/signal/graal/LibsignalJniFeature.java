package org.asamk.signal.graal;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Registers for JNI access everything in libsignal that its native library reaches by name, when
 * building the native image.
 * <p>
 * The reachability metadata recorded by the tracing agent covers only the calls that were made while
 * tracing. A call into libsignal that was not made then finds its classes missing at run time, and
 * the native library panics. This covers all of them, whichever calls are made.
 */
public class LibsignalJniFeature implements Feature {

    private static final String CLASS_SUFFIX = ".class";

    @Override
    public void beforeAnalysis(final BeforeAnalysisAccess access) {
        var classes = 0;
        var members = 0;
        for (final var entry : access.getApplicationClassPath()) {
            for (final var className : libsignalClassNames(entry)) {
                final var clazz = access.findClassByName(className);
                if (clazz == null) {
                    continue;
                }
                try {
                    if (!LibsignalJniMembers.isFoundByName(clazz)) {
                        continue;
                    }
                    RuntimeJNIAccess.register(clazz);
                    classes++;
                    for (final var member : LibsignalJniMembers.of(clazz)) {
                        if (member instanceof Executable executable) {
                            RuntimeJNIAccess.register(executable);
                        } else if (member instanceof Field field) {
                            RuntimeJNIAccess.register(field);
                        }
                        members++;
                    }
                } catch (LinkageError e) {
                    // The class refers to something that is not on the class path, such as Android's
                    // classes, so nothing here can load it either.
                }
            }
        }
        System.out.println("LibsignalJniFeature: registered " + classes + " classes and " + members + " members for JNI");
    }

    private static List<String> libsignalClassNames(final Path classPathEntry) {
        final var prefix = LibsignalJniMembers.PACKAGE.replace('.', '/');
        if (!Files.isRegularFile(classPathEntry)) {
            return List.of();
        }
        try (final var jar = new JarFile(classPathEntry.toFile())) {
            return jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(CLASS_SUFFIX))
                    .map(name -> name.substring(0, name.length() - CLASS_SUFFIX.length()).replace('/', '.'))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
