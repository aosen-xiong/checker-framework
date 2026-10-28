package org.checkerframework.checker.mutability;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.DefaultTypeHierarchy;
import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVariable;

/**
 * The type hierarchy of the Mutability Checker.
 *
 * <p>It adds limited covariance for type arguments. The head qualifier of a type argument may widen
 * from {@code @Mutable}, {@code @Immutable}, or {@code @Readonly} to {@code @MutabilityLost}, so
 * {@code q C<@MutabilityLost D>} is a supertype of {@code q C<@Mutable D>}. The same relation
 * applies recursively to nested type arguments and to the qualifier written on a type-variable use.
 * {@code @ReceiverDependentMutable} does not widen: the relation has to survive viewpoint
 * adaptation, and through a bottom receiver {@code @ReceiverDependentMutable} adapts to bottom
 * while {@code @MutabilityLost} stays {@code @MutabilityLost}. {@code @PolyMutable} and array type
 * arguments do not widen either. Neither is part of the model's relation.
 */
public class MutabilityTypeHierarchy extends DefaultTypeHierarchy {
    /** The mutability type factory. */
    private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

    /**
     * Creates a new mutability type hierarchy.
     *
     * @param checker the checker
     * @param qualHierarchy the qualifier hierarchy
     * @param ignoreRawTypes whether to ignore raw type arguments
     * @param invariantArrayComponents whether array components are invariant
     * @param mutabilityTypeFactory the mutability type factory
     */
    public MutabilityTypeHierarchy(
            BaseTypeChecker checker,
            QualifierHierarchy qualHierarchy,
            boolean ignoreRawTypes,
            boolean invariantArrayComponents,
            MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory) {
        super(checker, qualHierarchy, ignoreRawTypes, invariantArrayComponents);
        this.mutabilityTypeFactory = mutabilityTypeFactory;
    }

    @Override
    protected boolean isContainedBy(
            AnnotatedTypeMirror inside, AnnotatedTypeMirror outside, boolean canBeCovariant) {
        // Check covariance first. The superclass memoizes its result, including false, in a visit
        // history it shares with the equality comparer, so checking it first could let a cached
        // false hide a pair that covariance accepts.
        if (isLostCovariantArgument(inside, outside)) {
            return true;
        }
        return super.isContainedBy(inside, outside, canBeCovariant);
    }

    /**
     * Returns true if the type argument {@code inside} is related to the type argument {@code
     * outside} by limited covariance: the same class or the same type variable, a head qualifier
     * that is equal or widens to {@code @MutabilityLost}, and nested type arguments related the
     * same way. A wildcard or captured type variable is compared only by {@link #isContainedBy}.
     *
     * @param inside the type argument of the subtype
     * @param outside the type argument of the supertype
     * @return true if {@code inside} is related to {@code outside} by limited covariance
     */
    private boolean isLostCovariantArgument(
            AnnotatedTypeMirror inside, AnnotatedTypeMirror outside) {
        if (outside.getKind() == TypeKind.WILDCARD
                || inside.getKind() == TypeKind.WILDCARD
                || TypesUtils.isCapturedTypeVariable(outside.getUnderlyingType())
                || TypesUtils.isCapturedTypeVariable(inside.getUnderlyingType())) {
            return false;
        }
        if (inside.getKind() == TypeKind.DECLARED && outside.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType insideDeclared = (AnnotatedDeclaredType) inside;
            AnnotatedDeclaredType outsideDeclared = (AnnotatedDeclaredType) outside;
            DeclaredType insideUnderlying = insideDeclared.getUnderlyingType();
            DeclaredType outsideUnderlying = outsideDeclared.getUnderlyingType();
            if (!insideUnderlying.asElement().equals(outsideUnderlying.asElement())
                    || !isHeadWidening(inside, outside)) {
                return false;
            }
            List<? extends AnnotatedTypeMirror> insideArgs = insideDeclared.getTypeArguments();
            List<? extends AnnotatedTypeMirror> outsideArgs = outsideDeclared.getTypeArguments();
            if (insideArgs.size() != outsideArgs.size()) {
                return false;
            }
            for (int i = 0; i < insideArgs.size(); i++) {
                if (!isContainedBy(insideArgs.get(i), outsideArgs.get(i), false)) {
                    return false;
                }
            }
            return true;
        }
        if (inside.getKind() == TypeKind.TYPEVAR && outside.getKind() == TypeKind.TYPEVAR) {
            // A bare type-variable use has no primary qualifier and is compared only by
            // isContainedBy. An explicitly qualified use widens like a class type argument.
            return TypesUtils.areSame(
                            (TypeVariable) inside.getUnderlyingType(),
                            (TypeVariable) outside.getUnderlyingType())
                    && isHeadWidening(inside, outside);
        }
        return false;
    }

    /**
     * Returns true if the primary qualifier of {@code inside} is equal to that of {@code outside},
     * or is {@code @Mutable}, {@code @Immutable}, or {@code @Readonly} while that of {@code
     * outside} is {@code @MutabilityLost}. Both types must have a primary mutability qualifier.
     *
     * @param inside the type argument of the subtype
     * @param outside the type argument of the supertype
     * @return true if the head qualifier of {@code inside} is equal or widens to that of {@code
     *     outside}
     */
    private boolean isHeadWidening(AnnotatedTypeMirror inside, AnnotatedTypeMirror outside) {
        AnnotationMirror insideQualifier =
                inside.getAnnotationInHierarchy(mutabilityTypeFactory.READONLY);
        AnnotationMirror outsideQualifier =
                outside.getAnnotationInHierarchy(mutabilityTypeFactory.READONLY);
        if (insideQualifier == null || outsideQualifier == null) {
            return false;
        }
        if (AnnotationUtils.areSame(insideQualifier, outsideQualifier)) {
            return true;
        }
        return AnnotationUtils.areSame(outsideQualifier, mutabilityTypeFactory.LOST)
                && (AnnotationUtils.areSame(insideQualifier, mutabilityTypeFactory.MUTABLE)
                        || AnnotationUtils.areSame(insideQualifier, mutabilityTypeFactory.IMMUTABLE)
                        || AnnotationUtils.areSame(
                                insideQualifier, mutabilityTypeFactory.READONLY));
    }
}
