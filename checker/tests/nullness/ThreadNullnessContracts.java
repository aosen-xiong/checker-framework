// @below-java17-jdk-skip-test
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Consumer contracts from the pinned Thread and ThreadGroup Javadoc. Compile-only. */
@AnnotatedFor("nullness")
class ThreadNullnessContracts {
    void accepted(Thread thread, ThreadGroup group, Throwable failure) {
        new Thread(null, null, "worker", 0, false);
        new Thread(null, null, "worker", 0);
        new Thread((Runnable) null);
        new Thread((ThreadGroup) null, "worker");
        new ThreadGroup((String) null);
        new ThreadGroup(group, null);
        group.parentOf(null);
        thread.setContextClassLoader(null);
        thread.setUncaughtExceptionHandler(null);
        Thread.setDefaultUncaughtExceptionHandler(null);
        thread.getName().length();
        thread.getState().name();
        thread.getStackTrace()[0].getClassName().length();
        Thread.getAllStackTraces().values().iterator().next()[0].getClassName().length();
        Thread.currentThread().getName().length();
        Thread[] populatedThreads = {thread};
        @Nullable Thread[] optionalThreads = {null};
        ThreadGroup[] populatedGroups = {group};
        @Nullable ThreadGroup[] optionalGroups = {null};
        Thread.enumerate(populatedThreads);
        Thread.enumerate(optionalThreads);
        group.enumerate(populatedThreads, false);
        group.enumerate(optionalThreads, true);
        group.enumerate(populatedGroups, false);
        group.enumerate(optionalGroups, true);
        group.uncaughtException(thread, failure);
    }

    void rejected(
            Thread thread,
            ThreadGroup group,
            Thread.UncaughtExceptionHandler handler,
            Throwable failure) {
        // :: error: (argument.type.incompatible)
        new Thread(null, null, null, 0, false);
        // :: error: (argument.type.incompatible)
        thread.setName(null);
        // :: error: (argument.type.incompatible)
        Thread.holdsLock(null);
        // :: error: (argument.type.incompatible)
        new ThreadGroup(null, "group");
        // :: error: (argument.type.incompatible)
        Thread.enumerate(null);
        // :: error: (argument.type.incompatible)
        group.enumerate((Thread[]) null);
        // :: error: (argument.type.incompatible)
        group.enumerate((ThreadGroup[]) null, false);
        // :: error: (dereference.of.nullable)
        thread.getThreadGroup().activeCount();
        // :: error: (dereference.of.nullable)
        thread.getContextClassLoader().getName();
        // :: error: (dereference.of.nullable)
        thread.getUncaughtExceptionHandler().uncaughtException(thread, failure);
        // :: error: (dereference.of.nullable)
        Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, failure);
        // :: error: (dereference.of.nullable)
        group.getParent().activeCount();
        // :: error: (dereference.of.nullable)
        group.getName().length();
        // :: error: (argument.type.incompatible)
        handler.uncaughtException(null, failure);
        // :: error: (argument.type.incompatible)
        handler.uncaughtException(thread, null);
    }
}
