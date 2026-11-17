package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

/** Package-private consumer checks; run with the source gate, never executed. */
@AnnotatedFor("nullness")
class ThreadHelperContracts {
    @SuppressWarnings("removal")
    void optionalState(
            Thread thread,
            ThreadLocal<@Nullable Object> local,
            InheritableThreadLocal<@Nullable Object> inherited) {
        Thread.blockedOn(null);
        new Thread((Runnable) null, (java.security.AccessControlContext) null);
        local.createMap(thread, null);
        inherited.createMap(thread, null);
        Object value = local.getMap(thread);
        if (value != null) {
            value.toString();
        }
        ThreadLocal.ThreadLocalMap map = inherited.getMap(thread);
        if (map != null) {
            ThreadLocal.createInheritedMap(map);
        }
        ThreadLocal.ThreadLocalMap fresh = new ThreadLocal.ThreadLocalMap(local, null);
        new ThreadLocal.ThreadLocalMap.Entry(local, null);
        thread.parkBlocker = null;
        thread.threadLocals = null;
        thread.inheritableThreadLocals = null;
        Thread.WeakClassKey key =
                new Thread.WeakClassKey(Thread.class, new java.lang.ref.ReferenceQueue<>());
        key.equals(null);
    }
}
