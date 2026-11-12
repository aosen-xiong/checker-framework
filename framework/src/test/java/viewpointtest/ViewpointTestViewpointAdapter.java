package viewpointtest;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.type.AbstractViewpointAdapter;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;

import javax.lang.model.element.AnnotationMirror;

import viewpointtest.quals.Lost;
import viewpointtest.quals.PolyVP;
import viewpointtest.quals.ReceiverDependentQual;
import viewpointtest.quals.Top;

/** The viewpoint adapter for the Viewpoint Test Checker. */
public class ViewpointTestViewpointAdapter extends AbstractViewpointAdapter {

    /** The {@link Top} annotation. */
    private final AnnotationMirror TOP;

    /** The {@link PolyVP} annotation. */
    private final AnnotationMirror POLYVP;

    /** The {@link ReceiverDependentQual} annotation. */
    private final AnnotationMirror RECEIVERDEPENDENTQUAL;

    /** The {@link Lost} annotation. */
    private final AnnotationMirror LOST;

    /**
     * The class constructor.
     *
     * @param atypeFactory the type factory to use
     */
    public ViewpointTestViewpointAdapter(AnnotatedTypeFactory atypeFactory) {
        super(atypeFactory);
        TOP = ((ViewpointTestAnnotatedTypeFactory) atypeFactory).TOP;
        POLYVP = AnnotationBuilder.fromClass(atypeFactory.getElementUtils(), PolyVP.class);
        RECEIVERDEPENDENTQUAL =
                AnnotationBuilder.fromClass(
                        atypeFactory.getElementUtils(), ReceiverDependentQual.class);
        LOST = ((ViewpointTestAnnotatedTypeFactory) atypeFactory).LOST;
    }

    @Override
    protected AnnotationMirror extractAnnotationMirror(AnnotatedTypeMirror atm) {
        return atm.getAnnotationInHierarchy(TOP);
    }

    @Override
    protected AnnotationMirror extractAnnotationMirror(AnnotationMirrorSet annotations) {
        return atypeFactory.getQualifierHierarchy().findAnnotationInHierarchy(annotations, TOP);
    }

    @Override
    protected AnnotationMirror combineAnnotationWithAnnotation(
            AnnotationMirror receiverAnnotation, AnnotationMirror declaredAnnotation) {

        if (AnnotationUtils.areSame(declaredAnnotation, RECEIVERDEPENDENTQUAL)) {
            // A polymorphic receiver may be instantiated to Top, so it must also adapt to Lost.
            if (AnnotationUtils.areSame(receiverAnnotation, TOP)
                    || AnnotationUtils.areSame(receiverAnnotation, POLYVP)) {
                return LOST;
            } else {
                return receiverAnnotation;
            }
        }
        return declaredAnnotation;
    }

    @Override
    protected boolean widensRequalifiedUpperBounds() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A {@code @ReceiverDependentQual} type argument in an {@code extends} clause becomes the
     * qualifier of the reference, so every qualifier except {@code @Top} and {@code @Lost} widens
     * to {@code @Lost}. {@code @PolyVP} is a qualifier parameter that a call supplies, so it cannot
     * be widened.
     */
    @Override
    protected @Nullable AnnotationMirror widenTypeArgumentQualifier(AnnotationMirror qualifier) {
        if (AnnotationUtils.areSame(qualifier, TOP) || AnnotationUtils.areSame(qualifier, LOST)) {
            return qualifier;
        }
        if (AnnotationUtils.areSame(qualifier, POLYVP)) {
            return null;
        }
        return LOST;
    }
}
