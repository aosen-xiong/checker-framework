package org.checkerframework.checker.region;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;

public class RegionTypeVistor extends BaseTypeVisitor<RegionAnnotatedTypeFactory> {

    public RegionTypeVistor(BaseTypeChecker checker) {
        super(checker);
    }

    @Override
    public RegionAnnotatedTypeFactory createTypeFactory() {
        return new RegionAnnotatedTypeFactory(checker);
    }
}
