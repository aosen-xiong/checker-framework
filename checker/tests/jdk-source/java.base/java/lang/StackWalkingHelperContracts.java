package java.lang;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.AnnotatedFor;

import java.util.Set;

/** Positive private-API and helper-postcondition controls; never executed. */
@AnnotatedFor("nullness")
class StackWalkingHelperContracts {
    void liveState(LiveStackFrame frame, StackFrameInfo info) {
        Object local = frame.getLocals()[0];
        Object operand = frame.getStack()[0];
        Object monitor = frame.getMonitors()[0];
        if (local != null) local.toString();
        if (operand != null) operand.toString();
        if (monitor != null) monitor.toString();
        frame.toStackTraceElement().getClassName().length();
        LiveStackFrame.getStackWalker().getClass();
        LiveStackFrame.getStackWalker(Set.of()).getClass();
        LiveStackFrameInfo.asPrimitive(1).intValue();
        LiveStackFrameInfo.asPrimitive(1L).longValue();
        String file = info.getFileName();
        if (file != null) file.length();
    }

    void initialize(StackStreamFactory.StackFrameTraverser<@Nullable String> traverser) {
        traverser.initFrameBuffer();
        traverser.frameBuffer.curBatchFrameCount();
    }

    @Nullable String nullableTraversal(StackWalker walker) {
        return StackStreamFactory.makeStackTraverser(walker, frames -> (String) null).walk();
    }
}
