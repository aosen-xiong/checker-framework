package org.checkerframework.checker.mutability;

import org.checkerframework.framework.type.AbstractViewpointAdapter;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;

import javax.lang.model.element.AnnotationMirror;

/**
 * Viewpoint adapter for mutability qualifiers.
 *
 * <p>Most mutability qualifiers are stable under viewpoint adaptation. Only
 * {@code @ReceiverDependentMutable} depends on the receiver: mutable and immutable receivers
 * preserve their own qualifier, while a readonly receiver loses the precise mutability information
 * and adapts to {@code @MutabilityLost}.
 */
public class MutabilityViewpointAdapter extends AbstractViewpointAdapter {
    /** The mutability type factory. */
    private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

    /**
     * Create a new {@link MutabilityViewpointAdapter}.
     *
     * @param atypeFactory the type factory
     */
    public MutabilityViewpointAdapter(AnnotatedTypeFactory atypeFactory) {
        super(atypeFactory);
        mutabilityTypeFactory = (MutabilityNoInitAnnotatedTypeFactory) atypeFactory;
    }

    @Override
    protected AnnotationMirror extractAnnotationMirror(AnnotatedTypeMirror atm) {
        return atm.getAnnotationInHierarchy(mutabilityTypeFactory.READONLY);
    }

    @Override
    protected AnnotationMirror combineAnnotationWithAnnotation(
            AnnotationMirror receiverAnnotation, AnnotationMirror declaredAnnotation) {
        if (declaredAnnotation == null) {
            declaredAnnotation = mutabilityTypeFactory.READONLY;
        }

        if (AnnotationUtils.areSame(
                declaredAnnotation, mutabilityTypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
            if (AnnotationUtils.areSame(receiverAnnotation, mutabilityTypeFactory.READONLY)) {
                return mutabilityTypeFactory.LOST;
            }
            return receiverAnnotation;
        }

        if (isFixedQualifier(declaredAnnotation)) {
            return declaredAnnotation;
        }

        throw new BugInCF("Unknown declared qualifier: " + declaredAnnotation);
    }

    /**
     * Returns true if {@code annotation} is a mutability qualifier that is unchanged by viewpoint
     * adaptation.
     *
     * @param annotation the annotation to test
     * @return true if viewpoint adaptation returns {@code annotation} unchanged
     */
    private boolean isFixedQualifier(AnnotationMirror annotation) {
        return AnnotationUtils.areSame(annotation, mutabilityTypeFactory.READONLY)
                || AnnotationUtils.areSame(annotation, mutabilityTypeFactory.MUTABLE)
                || AnnotationUtils.areSame(annotation, mutabilityTypeFactory.IMMUTABLE)
                || AnnotationUtils.areSame(annotation, mutabilityTypeFactory.BOTTOM)
                || AnnotationUtils.areSame(annotation, mutabilityTypeFactory.POLY_MUTABLE)
                || AnnotationUtils.areSame(annotation, mutabilityTypeFactory.LOST);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The receiver position uses a different rule from value adaptation:
     *
     * <pre>
     *   value:    &#64;Readonly |&gt; &#64;ReceiverDependentMutable = &#64;MutabilityLost
     *   receiver: &#64;Readonly |&gt; &#64;ReceiverDependentMutable = &#64;Readonly
     * </pre>
     *
     * <p>Losing the mutability is correct for a field or a return type: reading receiver-dependent
     * state through a readonly reference genuinely loses the precise mutability. It is wrong for a
     * receiver. A receiver-dependent method imposes no requirement of its own on the receiver — it
     * adapts to whatever the caller has — so adapting its declared receiver to {@code @MutabilityLost}
     * makes every such method uncallable on a {@code @Readonly} reference, and rules out ordinary
     * read-only uses like passing a collection to a method that only iterates it.
     */
    @Override
    protected AnnotatedTypeMirror combineTypeWithReceiverType(
            AnnotatedTypeMirror receiverType, AnnotatedTypeMirror declaredReceiverType) {
        AnnotationMirror declared =
                declaredReceiverType.getAnnotationInHierarchy(mutabilityTypeFactory.READONLY);
        if (declared != null
                && AnnotationUtils.areSame(
                        declared, mutabilityTypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
            AnnotationMirror callSite =
                    receiverType.getAnnotationInHierarchy(mutabilityTypeFactory.READONLY);
            if (callSite != null) {
                AnnotatedTypeMirror adapted = declaredReceiverType.shallowCopy();
                adapted.replaceAnnotation(callSite);
                return adapted;
            }
        }
        return combineTypeWithType(receiverType, declaredReceiverType);
    }
}
