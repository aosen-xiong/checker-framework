package org.checkerframework.framework.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedIntersectionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable.TypeVariableUsageKind;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.Pair;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

/**
 * Abstract utility class for performing viewpoint adaptation.
 *
 * <p>This class contains the common logic for extracting and inserting viewpoint adapted
 * annotations into the corresponding types for member/field access, constructor and method
 * invocations, and type parameter bound instantiations.
 *
 * <p>Subclasses implement the computation of the precise viewpoint adapted type given a receiver
 * type and a declared type, and implement how to extract the qualifier given an ATM.
 */
public abstract class AbstractViewpointAdapter implements ViewpointAdapter {

    /** The annotated type factory. */
    protected final AnnotatedTypeFactory atypeFactory;

    /**
     * Construct an abstract viewpoint adapter with the given type factory.
     *
     * @param atypeFactory the type factory to use
     */
    protected AbstractViewpointAdapter(final AnnotatedTypeFactory atypeFactory) {
        this.atypeFactory = atypeFactory;
    }

    @Override
    public void viewpointAdaptMember(
            AnnotatedTypeMirror receiverType,
            Element memberElement,
            AnnotatedTypeMirror memberType) {
        if (!shouldAdaptMember(memberType, memberElement)) {
            return;
        }

        AnnotatedTypeMirror decltype = atypeFactory.getAnnotatedType(memberElement);
        AnnotatedTypeMirror combinedType = combineTypeWithType(receiverType, decltype);
        memberType.replaceAnnotations(combinedType.getAnnotationsField());
        if (memberType.getKind() == TypeKind.DECLARED
                && combinedType.getKind() == TypeKind.DECLARED) {
            AnnotatedDeclaredType adtType = (AnnotatedDeclaredType) memberType;
            AnnotatedDeclaredType adtCombinedType = (AnnotatedDeclaredType) combinedType;
            adtType.setTypeArguments(adtCombinedType.getTypeArguments());
        } else if (memberType.getKind() == TypeKind.ARRAY
                && combinedType.getKind() == TypeKind.ARRAY) {
            AnnotatedArrayType aatType = (AnnotatedArrayType) memberType;
            AnnotatedArrayType aatCombinedType = (AnnotatedArrayType) combinedType;
            aatType.setComponentType(aatCombinedType.getComponentType());
        } else if (memberType.getKind() == TypeKind.TYPEVAR
                && combinedType.getKind() == TypeKind.TYPEVAR) {
            // A type variable's bounds are adapted like any other part of the member's type, as
            // in "X item" read through a "@Q Box<X>" receiver inside Box.
            AnnotatedTypeVariable atvType = (AnnotatedTypeVariable) memberType;
            AnnotatedTypeVariable atvCombinedType = (AnnotatedTypeVariable) combinedType;
            if (atvType.getUnderlyingType()
                    .asElement()
                    .equals(atvCombinedType.getUnderlyingType().asElement())) {
                atvType.setUpperBound(atvCombinedType.getUpperBound());
                atvType.setLowerBound(atvCombinedType.getLowerBound());
            }
        }
    }

    /**
     * Determines whether a particular member should be viewpoint adapted or not. The default
     * implementation adapts all members except for local variables and method formal parameters.
     *
     * @param type type of the member, used to decide whether a member should be viewpoint adapted
     *     or not. A subclass of {@link ViewpointAdapter} may disable viewpoint adaptation for
     *     elements based on their types.
     * @param element element of the member
     * @return true if the member needs viewpoint adaptation
     */
    protected boolean shouldAdaptMember(AnnotatedTypeMirror type, Element element) {
        if (element.getKind() == ElementKind.LOCAL_VARIABLE
                || element.getKind() == ElementKind.PARAMETER) {
            return false;
        }
        return true;
    }

    @Override
    public void viewpointAdaptConstructor(
            AnnotatedTypeMirror receiverType,
            ExecutableElement constructorElt,
            AnnotatedExecutableType constructorType) {
        // 1. Make a copy of constructorType before type variables are substituted.
        AnnotatedExecutableType unsubstitutedConstructorType = constructorType.deepCopy();

        // 2. Viewpoint-adapt constructor parameter types, type variable bounds, and return type.
        List<AnnotatedTypeMirror> parameterTypes = unsubstitutedConstructorType.getParameterTypes();
        List<AnnotatedTypeVariable> typeVariables = unsubstitutedConstructorType.getTypeVariables();
        AnnotatedTypeMirror constructorReturn = unsubstitutedConstructorType.getReturnType();

        IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> mappings =
                new IdentityHashMap<>();

        // 2a. Adapt parameter types.
        for (AnnotatedTypeMirror parameterType : parameterTypes) {
            AnnotatedTypeMirror p = combineTypeWithType(receiverType, parameterType);
            mappings.put(parameterType, p);
        }

        // 2b. Adapt upper and lower bounds of constructor type variables.
        for (AnnotatedTypeVariable typeVariable : typeVariables) {
            AnnotatedTypeMirror adaptedUpper =
                    combineTypeWithType(receiverType, typeVariable.getUpperBound());
            mappings.put(typeVariable.getUpperBound(), adaptedUpper);

            AnnotatedTypeMirror adaptedLower =
                    combineTypeWithType(receiverType, typeVariable.getLowerBound());
            mappings.put(typeVariable.getLowerBound(), adaptedLower);
        }

        // 2c. Adapt constructor return type.
        AnnotatedTypeMirror cr = combineTypeWithType(receiverType, constructorReturn);
        mappings.put(constructorReturn, cr);

        // 3. Replace components using AnnotatedTypeCopierWithReplacement.
        unsubstitutedConstructorType =
                (AnnotatedExecutableType)
                        AnnotatedTypeCopierWithReplacement.replace(
                                unsubstitutedConstructorType, mappings);

        // 4. Update target constructor type in place with adapted components.
        constructorType.setParameterTypes(unsubstitutedConstructorType.getParameterTypes());
        constructorType.setTypeVariables(unsubstitutedConstructorType.getTypeVariables());
        constructorType.setReturnType(unsubstitutedConstructorType.getReturnType());
        // Recompute the vararg type to ensure it corresponds to the newly updated parameter list.
        constructorType.computeVarargType();
    }

    @Override
    public void viewpointAdaptMethod(
            AnnotatedTypeMirror receiverType,
            ExecutableElement methodElt,
            AnnotatedExecutableType methodType) {
        // 1. Check whether the method should be viewpoint-adapted (e.g. skip static methods).
        if (!shouldAdaptMethod(methodElt)) {
            return;
        }

        // 2. Make a copy of methodType before type variables are substituted.
        AnnotatedExecutableType unsubstitutedMethodType = methodType.deepCopy();

        // 3. Viewpoint-adapt parameter types, type variable bounds, return type, and receiver.
        List<AnnotatedTypeMirror> parameterTypes = unsubstitutedMethodType.getParameterTypes();
        List<AnnotatedTypeVariable> typeVariables = unsubstitutedMethodType.getTypeVariables();
        AnnotatedTypeMirror returnType = unsubstitutedMethodType.getReturnType();
        AnnotatedTypeMirror methodReceiver = unsubstitutedMethodType.getReceiverType();

        IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> mappings =
                new IdentityHashMap<>();

        // 3a. Adapt parameter types.
        for (AnnotatedTypeMirror parameterType : parameterTypes) {
            AnnotatedTypeMirror p = combineTypeWithType(receiverType, parameterType);
            mappings.put(parameterType, p);
        }

        // 3b. Adapt upper and lower bounds of method type variables.
        for (AnnotatedTypeVariable typeVariable : typeVariables) {
            AnnotatedTypeMirror adaptedUpper =
                    combineTypeWithType(receiverType, typeVariable.getUpperBound());
            mappings.put(typeVariable.getUpperBound(), adaptedUpper);

            AnnotatedTypeMirror adaptedLower =
                    combineTypeWithType(receiverType, typeVariable.getLowerBound());
            mappings.put(typeVariable.getLowerBound(), adaptedLower);
        }

        // 3c. Adapt non-void return type.
        if (returnType.getKind() != TypeKind.VOID) {
            AnnotatedTypeMirror r = combineTypeWithType(receiverType, returnType);
            mappings.put(returnType, r);
        }

        // 3d. Adapt method receiver type.
        if (methodReceiver != null) {
            AnnotatedTypeMirror mr = combineTypeWithReceiverType(receiverType, methodReceiver);
            mappings.put(methodReceiver, mr);
        }

        // 4. Replace components using AnnotatedTypeCopierWithReplacement.
        unsubstitutedMethodType =
                (AnnotatedExecutableType)
                        AnnotatedTypeCopierWithReplacement.replace(
                                unsubstitutedMethodType, mappings);

        // 5. Update target method type in place with adapted components.
        // Because we can't viewpoint adapt asMemberOf result, we adapt the declared method first,
        // and set the corresponding parts on the asMemberOf result.
        methodType.setReturnType(unsubstitutedMethodType.getReturnType());
        methodType.setReceiverType(unsubstitutedMethodType.getReceiverType());
        methodType.setParameterTypes(unsubstitutedMethodType.getParameterTypes());
        methodType.setTypeVariables(unsubstitutedMethodType.getTypeVariables());
        // Recompute the vararg type to ensure it corresponds to the newly updated parameter list.
        methodType.computeVarargType();
    }

    /**
     * Determine if an invocation of the given method needs to be adapted.
     *
     * @param element the executable element for a method
     * @return true if an invocation of the executable element needs to be adapted
     */
    protected boolean shouldAdaptMethod(ExecutableElement element) {
        return !ElementUtils.isStatic(element);
    }

    @Override
    public void viewpointAdaptTypeParameterBounds(
            AnnotatedTypeMirror receiverType,
            List<AnnotatedTypeParameterBounds> typeParameterBounds) {
        // Update in place: callers below assume the list is the same mutable list they passed in.
        for (int i = 0, n = typeParameterBounds.size(); i < n; ++i) {
            AnnotatedTypeParameterBounds typeParameterBound = typeParameterBounds.get(i);
            AnnotatedTypeMirror adaptedUpper =
                    combineTypeWithType(receiverType, typeParameterBound.getUpperBound());
            AnnotatedTypeMirror adaptedLower =
                    combineTypeWithType(receiverType, typeParameterBound.getLowerBound());
            typeParameterBounds.set(
                    i, new AnnotatedTypeParameterBounds(adaptedUpper, adaptedLower));
        }
    }

    @Override
    public AnnotationMirrorSet viewpointAdaptTypeDeclarationBounds(
            AnnotationMirrorSet viewpointBounds, AnnotatedTypeMirror declarationBoundType) {
        AnnotationMirror viewpointAnnotation = extractAnnotationMirror(viewpointBounds);
        return combineAnnotationWithType(viewpointAnnotation, declarationBoundType)
                .getAnnotations();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation widens each qualifier of the type arguments with {@link
     * #widenTypeArgumentQualifier}. It applies only when the bound is a class type whose type
     * arguments are ground: built from class, array, and primitive types at every depth, with no
     * type variable, wildcard, or qualifier that cannot be widened. A later substitution can supply
     * a qualifier inside a type variable, which widening here cannot reach.
     */
    @Override
    public @Nullable AnnotatedTypeMirror widenRequalifiedUpperBound(
            AnnotatedTypeMirror upperBound) {
        if (upperBound.getKind() != TypeKind.DECLARED) {
            return null;
        }
        AnnotationMirror mainQualifier = extractAnnotationMirror(upperBound);
        if (mainQualifier == null || atypeFactory.getQualifierHierarchy().isBottom(mainQualifier)) {
            // Only null has a bottom-qualified type, so its view of the bound reaches no object.
            // Type argument inference instantiates a type parameter to such a use.
            return null;
        }
        List<AnnotatedTypeMirror> typeArgs =
                ((AnnotatedDeclaredType) upperBound).getTypeArguments();
        if (typeArgs.isEmpty()) {
            return null;
        }
        boolean changes = false;
        for (AnnotatedTypeMirror typeArg : typeArgs) {
            Boolean argChanges = groundAndWidens(typeArg);
            if (argChanges == null) {
                return null;
            }
            changes |= argChanges;
        }
        if (!changes) {
            return null;
        }
        AnnotatedDeclaredType widened = (AnnotatedDeclaredType) upperBound.deepCopy(true);
        for (AnnotatedTypeMirror typeArg : widened.getTypeArguments()) {
            widenGround(typeArg);
        }
        return widened;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation applies only to an adapter that widens type arguments, which {@link
     * #widensRequalifiedUpperBounds} tells. It returns false when the bound is a class type with a
     * type argument that is not ground in the sense of {@link #widenRequalifiedUpperBound}: it
     * contains a type variable, a wildcard, or a qualifier that cannot be widened, such as a
     * qualifier parameter.
     */
    @Override
    public boolean hasRequalifiedUseView(AnnotatedTypeMirror upperBound) {
        if (!widensRequalifiedUpperBounds() || upperBound.getKind() != TypeKind.DECLARED) {
            return true;
        }
        AnnotationMirror mainQualifier = extractAnnotationMirror(upperBound);
        if (mainQualifier == null || atypeFactory.getQualifierHierarchy().isBottom(mainQualifier)) {
            // As in widenRequalifiedUpperBound: only null has this type.
            return true;
        }
        for (AnnotatedTypeMirror typeArg :
                ((AnnotatedDeclaredType) upperBound).getTypeArguments()) {
            if (groundAndWidens(typeArg) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if this adapter widens the type arguments of the upper bound of a type-variable
     * use written {@code @q X}, that is, if it implements {@link #widenTypeArgumentQualifier}. Only
     * such an adapter restricts the uses whose type arguments it cannot widen; see {@link
     * #hasRequalifiedUseView}.
     *
     * <p>The default implementation returns false.
     *
     * @return true if this adapter widens type arguments
     */
    protected boolean widensRequalifiedUpperBounds() {
        return false;
    }

    /**
     * Returns the qualifier that {@code qualifier} is widened to in a type argument of the upper
     * bound of a type-variable use written {@code @q X}; see {@link #widenRequalifiedUpperBound}. A
     * qualifier that a receiver-dependent qualifier can become through some reference is widened to
     * the qualifier that forgets which one it was. A qualifier it cannot become is returned
     * unchanged.
     *
     * <p>The default implementation returns null, so that no upper bound is widened.
     *
     * @param qualifier a qualifier in this adapter's hierarchy
     * @return the widened qualifier, or null if {@code qualifier} cannot be widened soundly, such
     *     as a qualifier parameter that a call supplies later
     */
    protected @Nullable AnnotationMirror widenTypeArgumentQualifier(AnnotationMirror qualifier) {
        return null;
    }

    /**
     * Returns whether widening would change {@code type}, or null if {@code type} is not ground.
     *
     * @param type a type argument, or a component of one
     * @return true if widening changes {@code type}, false if it does not, and null if {@code type}
     *     is not ground
     */
    private @Nullable Boolean groundAndWidens(AnnotatedTypeMirror type) {
        TypeKind kind = type.getKind();
        if (kind != TypeKind.DECLARED && kind != TypeKind.ARRAY && !kind.isPrimitive()) {
            return null;
        }
        AnnotationMirror qualifier = extractAnnotationMirror(type);
        if (qualifier == null) {
            return null;
        }
        AnnotationMirror widened = widenTypeArgumentQualifier(qualifier);
        if (widened == null) {
            return null;
        }
        boolean changes = !AnnotationUtils.areSame(qualifier, widened);
        List<AnnotatedTypeMirror> components;
        if (kind == TypeKind.DECLARED) {
            components = ((AnnotatedDeclaredType) type).getTypeArguments();
        } else if (kind == TypeKind.ARRAY) {
            components = Collections.singletonList(((AnnotatedArrayType) type).getComponentType());
        } else {
            components = Collections.emptyList();
        }
        for (AnnotatedTypeMirror component : components) {
            Boolean componentChanges = groundAndWidens(component);
            if (componentChanges == null) {
                return null;
            }
            changes |= componentChanges;
        }
        return changes;
    }

    /**
     * Widens every qualifier of {@code type}, which {@link #groundAndWidens} has found ground, in
     * place.
     *
     * @param type a ground type argument, or a component of one
     */
    private void widenGround(AnnotatedTypeMirror type) {
        AnnotationMirror qualifier = extractAnnotationMirror(type);
        AnnotationMirror widened = widenTypeArgumentQualifier(qualifier);
        if (widened != null) {
            type.replaceAnnotation(widened);
        }
        if (type.getKind() == TypeKind.DECLARED) {
            for (AnnotatedTypeMirror typeArg : ((AnnotatedDeclaredType) type).getTypeArguments()) {
                widenGround(typeArg);
            }
        } else if (type.getKind() == TypeKind.ARRAY) {
            widenGround(((AnnotatedArrayType) type).getComponentType());
        }
    }

    @Override
    public AnnotatedTypeMirror viewpointAdaptType(
            AnnotatedTypeMirror receiverType, AnnotatedTypeMirror declaredType) {
        // Every caller passes a type whose type arguments are already actual type arguments as
        // seen from the receiver: a direct supertype of the receiver that SupertypeFinder has
        // substituted, a copy of the receiver itself, or the interface that an anonymous class
        // implements.  A type variable in it that the receiver's class does not declare is such
        // an actual type argument, not a formal type parameter of one of the receiver's
        // supertypes, so it must not be looked up in the receiver's supertypes.  That lookup
        // would also compute the receiver's direct supertypes, whose post-processing calls this
        // method again with the same receiver, recursing until the stack overflows.
        return combineTypeWithType(receiverType, declaredType, false);
    }

    /**
     * Viewpoint-adapts a declared method RECEIVER against the call-site receiver.
     *
     * <p>Defaults to {@link #combineTypeWithType}, which is right for parameters, type-variable
     * bounds, and return types. A type system whose adaptation loses information for a
     * receiver-dependent qualifier may need a different rule here: a receiver-dependent method
     * imposes no requirement of its own on the receiver, so adapting its declared receiver the same
     * way as a value makes the method uncallable through such a reference.
     *
     * @param receiverType the call-site receiver type
     * @param declaredReceiverType the declared method receiver type
     * @return the adapted receiver type
     */
    protected AnnotatedTypeMirror combineTypeWithReceiverType(
            AnnotatedTypeMirror receiverType, AnnotatedTypeMirror declaredReceiverType) {
        return combineTypeWithType(receiverType, declaredReceiverType);
    }

    /**
     * Viewpoint adapt declared type to receiver type, and return the result atm
     *
     * @param receiver receiver type
     * @param declared declared type
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    protected AnnotatedTypeMirror combineTypeWithType(
            AnnotatedTypeMirror receiver, AnnotatedTypeMirror declared) {
        return combineTypeWithType(receiver, declared, true);
    }

    /**
     * Viewpoint adapt declared type to receiver type, and return the result atm
     *
     * @param receiver receiver type
     * @param declared declared type
     * @param searchSupertypes whether a type variable that the receiver's class does not declare is
     *     looked up among the type parameters of the receiver's supertypes; see {@link
     *     #substituteTVars}
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    private AnnotatedTypeMirror combineTypeWithType(
            AnnotatedTypeMirror receiver, AnnotatedTypeMirror declared, boolean searchSupertypes) {
        assert receiver != null && declared != null;

        AnnotatedTypeMirror result = declared;

        if (receiver.getKind() == TypeKind.TYPEVAR) {
            receiver = atypeFactory.getTypeVariableUseView((AnnotatedTypeVariable) receiver);
        }
        AnnotationMirror receiverAnnotation = extractAnnotationMirror(receiver);
        if (receiverAnnotation != null) {
            result = combineAnnotationWithType(receiverAnnotation, declared);
            result = substituteTVars(receiver, result, searchSupertypes);
        }

        return result;
    }

    /**
     * Extract the relevant qualifier from an {@link AnnotatedTypeMirror}.
     *
     * @param atm AnnotatedTypeMirror from which qualifier is extracted
     * @return extracted qualifier
     */
    protected abstract AnnotationMirror extractAnnotationMirror(AnnotatedTypeMirror atm);

    /**
     * Extracts the qualifier that this adapter adapts with, from a set of qualifiers. The
     * counterpart of {@link #extractAnnotationMirror(AnnotatedTypeMirror)} for a bare qualifier
     * set, such as a type's declaration bounds.
     *
     * @param annotations a set of qualifiers containing one in this adapter's hierarchy
     * @return the qualifier used for viewpoint adaptation
     */
    protected abstract AnnotationMirror extractAnnotationMirror(AnnotationMirrorSet annotations);

    /**
     * Combine receiver qualifiers with declared types. Qualifiers are extracted from declared types
     * to further perform viewpoint adaptation only between two qualifiers.
     *
     * @param receiverAnnotation receiver qualifier
     * @param declared declared type
     * @return {@link AnnotatedTypeMirror} after viewpoint adaptation
     */
    protected AnnotatedTypeMirror combineAnnotationWithType(
            AnnotationMirror receiverAnnotation, AnnotatedTypeMirror declared) {
        return new ViewpointAdaptationCopier(receiverAnnotation).visit(declared);
    }

    /**
     * An {@link AnnotatedTypeCopier} that recomputes an intersection type's primary annotation from
     * its bounds, after the copy has transformed them.
     *
     * <p>Both copiers below need this, and both terminate on a self-referential type because {@link
     * AnnotatedTypeCopier}'s original-to-copy map visits each type once.
     */
    private abstract static class SummarizeIntersectionCopier extends AnnotatedTypeCopier {

        /** Constructor for subclasses to call. */
        SummarizeIntersectionCopier() {}

        @Override
        public AnnotatedTypeMirror visitIntersection(
                AnnotatedIntersectionType original,
                IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> originalToCopy) {
            AnnotatedIntersectionType result =
                    (AnnotatedIntersectionType) super.visitIntersection(original, originalToCopy);
            // An intersection has no primary annotation of its own to transform; recompute it from
            // the bounds, which super has just transformed.  Do not clear it first:
            // AnnotatedIntersectionType#clearAnnotations also clears every bound, which would
            // discard those results before summarizeBounds reads them.
            result.summarizeBounds();
            return result;
        }
    }

    /**
     * Copies an annotated type graph, viewpoint-adapting each qualifier as it goes. Adaptation is
     * the copy's only difference from its original.
     *
     * <p>Using a copier is what makes a self-referential type terminate: the original-to-copy map
     * adapts each type once and points every later reference at that copy. The hand-rolled
     * traversal this replaced recursed until it overflowed the stack.
     */
    private final class ViewpointAdaptationCopier extends SummarizeIntersectionCopier {

        /** The receiver qualifier used for viewpoint adaptation. */
        private final AnnotationMirror receiverAnnotation;

        /**
         * Creates a copier that viewpoint-adapts qualifiers using {@code receiverAnnotation}.
         *
         * @param receiverAnnotation the receiver qualifier
         */
        private ViewpointAdaptationCopier(AnnotationMirror receiverAnnotation) {
            this.receiverAnnotation = receiverAnnotation;
        }

        @Override
        protected void maybeCopyPrimaryAnnotations(
                AnnotatedTypeMirror source, AnnotatedTypeMirror dest) {
            super.maybeCopyPrimaryAnnotations(source, dest);
            // A wildcard has no primary annotation.  A type variable's is adapted in
            // visitTypeVariable instead.  An intersection's is recomputed from its bounds
            // instead (see visitIntersection).  Any other kind is left as copied: the traversal
            // this replaced threw BugInCF rather than adapting one, so none reaches here.
            TypeKind kind = source.getKind();
            if (kind.isPrimitive()
                    || kind == TypeKind.DECLARED
                    || kind == TypeKind.ARRAY
                    || kind == TypeKind.NULL) {
                AnnotationMirror resultAnnotation =
                        combineAnnotationWithAnnotation(
                                receiverAnnotation, extractAnnotationMirror(source));
                dest.replaceAnnotation(resultAnnotation);
            }
        }

        /**
         * {@inheritDoc}
         *
         * <p>A type variable's primary annotation belongs to the usage rather than to the
         * declaration, so it is adapted here rather than in {@link #maybeCopyPrimaryAnnotations}:
         * {@link AnnotatedTypeCopier#visitTypeVariable} copies the usage kind and the usage-site
         * qualifiers from the original after building the copy, which would overwrite an adapted
         * qualifier written any earlier.
         *
         * <p>A usage written {@code @Q E} carries a qualifier of its own, so {@code @Q} is adapted
         * and the adapted qualifier recorded, and {@link TypeVariableSubstitutor} then keeps it in
         * place of the type argument's. A usage written bare takes its qualifier from the type
         * argument, so it is left alone.
         */
        @Override
        public AnnotatedTypeMirror visitTypeVariable(
                AnnotatedTypeVariable original,
                IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> originalToCopy) {
            // Adapt only when this copy is first created; a type that refers back to itself
            // reaches this method again and must not be adapted twice.
            boolean firstVisit = !originalToCopy.containsKey(original);
            AnnotatedTypeMirror copy = super.visitTypeVariable(original, originalToCopy);
            if (firstVisit
                    && copy instanceof AnnotatedTypeVariable
                    && original.getTypeVariableUsageKind() == TypeVariableUsageKind.REQUALIFYING) {
                AnnotationMirror resultAnnotation =
                        combineAnnotationWithAnnotation(
                                receiverAnnotation, extractAnnotationMirror(original));
                copy.replaceAnnotation(resultAnnotation);
                ((AnnotatedTypeVariable) copy)
                        .markAsRequalifyingTypeVariableUsage(resultAnnotation);
            }
            return copy;
        }
    }

    /**
     * Viewpoint adapt declared qualifier to receiver qualifier.
     *
     * @param receiverAnnotation receiver qualifier
     * @param declaredAnnotation declared qualifier
     * @return result qualifier after viewpoint adaptation
     */
    @SideEffectFree
    protected abstract AnnotationMirror combineAnnotationWithAnnotation(
            AnnotationMirror receiverAnnotation, AnnotationMirror declaredAnnotation);

    /**
     * If rhs contains or is a use of a type variable of lhs's class, substitutes lhs's actual type
     * argument for it and returns the result. Side-effect free: when there is anything to
     * substitute, rhs is copied and the copy is returned; when lhs is not a declared type there is
     * nothing to substitute and rhs itself is returned.
     *
     * @param lhs type from which type arguments are extracted to replace formal type parameters of
     *     rhs
     * @param rhs {@link AnnotatedTypeMirror} that might be, or contain, a formal type parameter
     * @param searchSupertypes whether to also substitute for a type variable that lhs's class does
     *     not declare but one of its supertypes does, with the type argument that lhs supplies for
     *     it through that supertype
     * @return a copy of rhs with its type parameters substituted
     */
    private AnnotatedTypeMirror substituteTVars(
            AnnotatedTypeMirror lhs, AnnotatedTypeMirror rhs, boolean searchSupertypes) {
        if (lhs.getKind() != TypeKind.DECLARED) {
            return rhs;
        }
        return new TypeVariableSubstitutionCopier((AnnotatedDeclaredType) lhs, searchSupertypes)
                .visit(rhs);
    }

    /** Performs the substitution described by {@link #substituteTVars}, as a copy. */
    private final class TypeVariableSubstitutionCopier extends SummarizeIntersectionCopier {

        /** The receiver from which actual type arguments are taken. */
        private final AnnotatedDeclaredType receiver;

        /** Whether to look up type variables among the type parameters of supertypes, too. */
        private final boolean searchSupertypes;

        /**
         * Creates a copier that substitutes type variables of {@code receiver}'s class.
         *
         * @param receiver the receiver
         * @param searchSupertypes whether to look up type variables among the type parameters of
         *     {@code receiver}'s supertypes, too
         */
        private TypeVariableSubstitutionCopier(
                AnnotatedDeclaredType receiver, boolean searchSupertypes) {
            this.receiver = receiver;
            this.searchSupertypes = searchSupertypes;
        }

        @Override
        public AnnotatedTypeMirror visitTypeVariable(
                AnnotatedTypeVariable original,
                IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> originalToCopy) {
            // A type variable is terminal, as it was in the hand-rolled traversal this replaced:
            // it is replaced by the receiver's actual type argument, or left alone if the receiver
            // supplies none.  Its bounds are not descended into, so it cannot start a cycle and
            // needs no originalToCopy entry.
            return getTypeVariableSubstitution(receiver, original, searchSupertypes);
        }
    }

    /**
     * Return actual type argument for formal type parameter "var" from "type"
     *
     * @param type type from which type arguments are extracted to replace "var"
     * @param var formal type parameter that needs real type arguments
     * @param searchSupertypes whether to look up {@code var} among the type parameters of {@code
     *     type}'s supertypes, too
     * @return Real type argument
     */
    private AnnotatedTypeMirror getTypeVariableSubstitution(
            AnnotatedDeclaredType type, AnnotatedTypeVariable var, boolean searchSupertypes) {
        Pair<AnnotatedDeclaredType, Integer> res = findDeclType(type, var, searchSupertypes);

        if (res == null) {
            return var;
        }

        AnnotatedDeclaredType decltype = res.first;
        int foundindex = res.second;

        List<AnnotatedTypeMirror> tas = decltype.getTypeArguments();
        AnnotatedTypeMirror typeArgument = tas.get(foundindex);
        if (isPlainUseOf(typeArgument, var)) {
            // The receiver passes the type parameter through unchanged, as the receiver of a
            // method does inside the parameter's own class.  The type argument is then var's own
            // type parameter, so substituting it would only undo the adaptation of var through
            // the receiver: of its bounds, and of its qualifier if the usage is written "@q E".
            // This is not a capture, so the caveat below does not apply.
            return var;
        }
        // return a copy, as we want to modify the type later.
        AnnotatedTypeMirror result = typeArgument.shallowCopy(true);
        if (var.getTypeVariableUsageKind() == TypeVariableUsageKind.REQUALIFYING
                && result.getKind() != TypeKind.TYPEVAR) {
            // Requalifying replaces the argument's own head qualifier.  A type variable -- in
            // particular a capture of a wildcard argument -- has no head qualifier to replace;
            // writing one propagates into its bounds (fixupBoundAnnotations) and leaves a type
            // whose shape no longer matches its declaration, which the parallel type scanners
            // reached from adjustMethodReceiver then reject.
            AnnotationMirrorSet requalifyingAnnotations =
                    var.getRequalifyingTypeVariableUsageAnnotations();
            if (!requalifyingAnnotations.isEmpty()) {
                result.replaceAnnotations(requalifyingAnnotations);
            }
        }
        if (result.getKind() == TypeKind.WILDCARD) {
            AnnotatedWildcardType wildcard = (AnnotatedWildcardType) result;
            // When substituting an unbounded wildcard for a bounded type variable, the shallow
            // copy might lose the reference to the formal type variable that provides its
            // effective extends bound. Preserve this bound so that subsequent subtype checks
            // do not treat the wildcard as implicitly bounded by Object.
            if (wildcard.getUnderlyingType().getExtendsBound() == null
                    && wildcard.getTypeVariable() == null
                    && wildcard.getSuperBound().getKind() == TypeKind.NULL) {
                wildcard.setExtendsBound(var.getUpperBound().deepCopy(true));
            }
        }
        return result;
    }

    /**
     * Returns true if {@code typeArgument} is a use of the same type parameter as {@code var},
     * written bare.
     *
     * @param typeArgument a type argument
     * @param var a type variable
     * @return true if {@code typeArgument} is a bare use of {@code var}'s type parameter
     */
    private static boolean isPlainUseOf(
            AnnotatedTypeMirror typeArgument, AnnotatedTypeVariable var) {
        if (typeArgument.getKind() != TypeKind.TYPEVAR) {
            return false;
        }
        AnnotatedTypeVariable typeVariable = (AnnotatedTypeVariable) typeArgument;
        return typeVariable.getTypeVariableUsageKind() == TypeVariableUsageKind.PLAIN
                && typeVariable
                        .getUnderlyingType()
                        .asElement()
                        .equals(var.getUnderlyingType().asElement());
    }

    /**
     * Find the type that declares the type parameter {@code var}, and the index (position) of
     * {@code var} among that type's type parameters.
     *
     * @param type type from which we infer actual type arguments
     * @param var formal type parameter
     * @param searchSupertypes whether to look up {@code var} among the type parameters of {@code
     *     type}'s supertypes, too
     * @return the type, which is {@code type} or one of its supertypes, whose declaration has
     *     {@code var} as a type parameter, paired with the index of {@code var} among its type
     *     parameters; null if {@code var} is not a type parameter of {@code type}, or, when {@code
     *     searchSupertypes} is true, of any of its supertypes
     */
    private Pair<AnnotatedDeclaredType, Integer> findDeclType(
            AnnotatedDeclaredType type, AnnotatedTypeVariable var, boolean searchSupertypes) {
        Element varelem = var.getUnderlyingType().asElement();

        DeclaredType dtype = type.getUnderlyingType();
        TypeElement el = (TypeElement) dtype.asElement();
        List<? extends TypeParameterElement> tparams = el.getTypeParameters();
        int foundindex = 0;

        for (TypeParameterElement tparam : tparams) {
            if (tparam.equals(varelem)) {
                // we found the right index!
                break;
            }
            ++foundindex;
        }

        if (foundindex >= tparams.size()) {
            if (!searchSupertypes) {
                return null;
            }
            // Didn't find the desired type => Head for super type of "type"!
            for (AnnotatedDeclaredType sup : type.directSupertypes()) {
                Pair<AnnotatedDeclaredType, Integer> res = findDeclType(sup, var, true);
                if (res != null) {
                    return res;
                }
            }
            // We reach this point if the variable wasn't found in any recursive call on ALL direct
            // supertypes.
            return null;
        }

        return Pair.of(type, foundindex);
    }
}
