package org.checkerframework.checker.region;

import org.checkerframework.common.aliasing.AliasingChecker;
import org.checkerframework.framework.source.AggregateChecker;
import org.checkerframework.framework.source.SourceChecker;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class UniquityChecker extends AggregateChecker {

    @Override
    protected Collection<Class<? extends SourceChecker>> getSupportedCheckers() {
        Set<Class<? extends SourceChecker>> checkers = new LinkedHashSet<>(2);
        checkers.add(RegionTypeChecker.class);
        checkers.add(AliasingChecker.class);

        return checkers;
    }
}
