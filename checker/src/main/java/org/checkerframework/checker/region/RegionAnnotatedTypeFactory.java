package org.checkerframework.checker.region;

import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.MostlyNoElementQualifierHierarchy;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.framework.util.QualifierKind;
import org.checkerframework.javacutil.AnnotationUtils;

import javax.lang.model.element.AnnotationMirror;

public class RegionAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

    @SuppressWarnings("this-escape")
    public RegionAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        postInit();
    }

    @Override
    protected QualifierHierarchy createQualifierHierarchy() {
        return new RegionAnnotatedTypeFactory.RegionQualifierHierarchy();
    }

    class RegionQualifierHierarchy extends MostlyNoElementQualifierHierarchy {
        protected RegionQualifierHierarchy() {
            super(
                    RegionAnnotatedTypeFactory.this.getSupportedTypeQualifiers(),
                    elements,
                    RegionAnnotatedTypeFactory.this);
        }

        @Override
        protected boolean isSubtypeWithElements(
                AnnotationMirror subAnno,
                QualifierKind subKind,
                AnnotationMirror superAnno,
                QualifierKind superKind) {
            // If the arena name is the same, then return true
            return AnnotationUtils.areSame(subAnno, superAnno);
        }

        @Override
        protected AnnotationMirror leastUpperBoundWithElements(
                AnnotationMirror a1,
                QualifierKind qualifierKind1,
                AnnotationMirror a2,
                QualifierKind qualifierKind2,
                QualifierKind lubKind) {
            return null;
        }

        @Override
        protected AnnotationMirror greatestLowerBoundWithElements(
                AnnotationMirror a1,
                QualifierKind qualifierKind1,
                AnnotationMirror a2,
                QualifierKind qualifierKind2,
                QualifierKind glbKind) {
            return null;
        }
    }
}
