package org.checkerframework.checker.mutability;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import org.checkerframework.checker.initialization.InitializationFieldAccessTreeAnnotator;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Immutable;
import org.checkerframework.checker.mutability.qual.MutabilityBottom;
import org.checkerframework.checker.mutability.qual.MutabilityLost;
import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.PolyMutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeParameterBounds;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;
import org.checkerframework.framework.type.SyntheticArrays;
import org.checkerframework.framework.type.TypeHierarchy;
import org.checkerframework.framework.type.ViewpointAdapter;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.LiteralTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.PropagationTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.DefaultForTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.DefaultQualifierForUseTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** Annotated type factory for the mutability checker. */
public class MutabilityNoInitAnnotatedTypeFactory
        extends GenericAnnotatedTypeFactory<
                MutabilityNoInitValue,
                MutabilityNoInitStore,
                MutabilityNoInitTransfer,
                MutabilityNoInitAnalysis> {
    /** The {@link Mutable} annotation. */
    protected final AnnotationMirror MUTABLE = AnnotationBuilder.fromClass(elements, Mutable.class);

    /** The {@link Immutable} annotation. */
    protected final AnnotationMirror IMMUTABLE =
            AnnotationBuilder.fromClass(elements, Immutable.class);

    /** The {@link Readonly} annotation. */
    protected final AnnotationMirror READONLY =
            AnnotationBuilder.fromClass(elements, Readonly.class);

    /** The {@link ReceiverDependentMutable} annotation. */
    protected final AnnotationMirror RECEIVER_DEPENDENT_MUTABLE =
            AnnotationBuilder.fromClass(elements, ReceiverDependentMutable.class);

    /** The {@link PolyMutable} annotation. */
    protected final AnnotationMirror POLY_MUTABLE =
            AnnotationBuilder.fromClass(elements, PolyMutable.class);

    /** The {@link MutabilityLost} annotation. */
    protected final AnnotationMirror LOST =
            AnnotationBuilder.fromClass(elements, MutabilityLost.class);

    /** The {@link MutabilityBottom} annotation. */
    protected final AnnotationMirror BOTTOM =
            AnnotationBuilder.fromClass(elements, MutabilityBottom.class);

    /** Tree kinds that bound a method mode: a method, or a class that nests one. */
    private static final Set<Tree.Kind> MODE_BOUNDARY_KINDS;

    static {
        Set<Tree.Kind> kinds = EnumSet.of(Tree.Kind.METHOD);
        kinds.addAll(TreeUtils.classTreeKinds());
        MODE_BOUNDARY_KINDS = Collections.unmodifiableSet(kinds);
    }

    /**
     * The modes of the trees currently being typed, innermost first. Every entry point that can
     * viewpoint-adapt a member pushes the mode of the tree it was given, so adaptation reads the
     * mode of the method whose body contains that tree.
     */
    private final Deque<MethodMode> methodModeStack = new ArrayDeque<>();

    /**
     * Create a new MutabilityNoInitAnnotatedTypeFactory.
     *
     * @param checker the BaseTypeChecker
     */
    @SuppressWarnings("this-escape")
    public MutabilityNoInitAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        addAliasedTypeAnnotation(org.jmlspecs.annotation.Readonly.class, READONLY);
        postInit();
    }

    @Override
    protected Set<Class<? extends Annotation>> createSupportedTypeQualifiers() {
        return new LinkedHashSet<>(
                Arrays.asList(
                        Readonly.class,
                        Mutable.class,
                        PolyMutable.class,
                        ReceiverDependentMutable.class,
                        Immutable.class,
                        MutabilityLost.class,
                        MutabilityBottom.class));
    }

    @Override
    protected ViewpointAdapter createViewpointAdapter() {
        return new MutabilityViewpointAdapter(this);
    }

    @Override
    protected TypeHierarchy createTypeHierarchy() {
        return new MutabilityTypeHierarchy(
                checker,
                getQualifierHierarchy(),
                ignoreRawTypeArguments,
                checker.hasOption("invariantArrays"),
                this);
    }

    /**
     * Returns the mutability viewpoint adapter.
     *
     * @return the viewpoint adapter, typed
     */
    public MutabilityViewpointAdapter getMutabilityViewpointAdapter() {
        return (MutabilityViewpointAdapter) viewpointAdapter;
    }

    /** Annotators are executed by the added order. Same for Type Annotator */
    @Override
    protected TreeAnnotator createTreeAnnotator() {
        List<TreeAnnotator> annotators = new ArrayList<>(5);
        annotators.add(new InitializationFieldAccessTreeAnnotator(this));
        annotators.add(new MutabilityPropagationTreeAnnotator(this));
        annotators.add(new LiteralTreeAnnotator(this));
        annotators.add(new MutabilityTreeAnnotator(this));
        return new ListTreeAnnotator(annotators);
    }

    @Override
    protected TypeAnnotator createTypeAnnotator() {
        // Adding order is important here. Because internally type annotators are using
        // addMissingAnnotations() method, so if one annotator already applied the annotations, the
        // others won't apply twice at the same location
        return new ListTypeAnnotator(
                super.createTypeAnnotator(),
                // The framework skips @DefaultFor on the primary type of local variables. Apply
                // it here so implicitly immutable local types override the @Readonly location
                // default.
                new DefaultForTypeAnnotator(this),
                new MutabilityEnumDefaultAnnotator(this));
    }

    /**
     * {@inheritDoc}
     *
     * <p>After <a href="https://github.com/eisop/checker-framework/pull/1810">pull request
     * #1810</a>, simplify or remove this override and its manually copied superclass tail.
     */
    @Override
    protected void postDirectSuperTypes(
            AnnotatedTypeMirror type, List<? extends AnnotatedTypeMirror> supertypes) {
        AnnotationMirrorSet annotations = type.getEffectiveAnnotations();
        for (AnnotatedTypeMirror supertype : supertypes) {
            if (!annotations.equals(supertype.getEffectiveAnnotations())) {
                supertype.clearAnnotations();
                supertype.addAnnotations(annotations);
            }
        }
        // Do not call super.postDirectSuperTypes. Its viewpoint adaptation asks Mutability's
        // viewpoint adapter for direct supertypes while direct supertypes are already being
        // computed, which causes infinite recursion for raw recursive types. Apply only the
        // GenericAnnotatedTypeFactory tail that adds computed annotations.
        if (type.getKind() == TypeKind.DECLARED) {
            for (AnnotatedTypeMirror supertype : supertypes) {
                Element elt = ((DeclaredType) supertype.getUnderlyingType()).asElement();
                addComputedTypeAnnotations(elt, supertype);
            }
        }
    }

    /**
     * Returns the mode annotations written on {@code method}, in {@link MethodMode} order. A
     * well-formed method has at most one.
     *
     * @param method a method
     * @return the modes declared on {@code method}; empty if none
     */
    public List<MethodMode> getDeclaredMethodModes(ExecutableElement method) {
        List<MethodMode> modes = new ArrayList<>(1);
        for (MethodMode mode : MethodMode.values()) {
            if (getDeclAnnotation(method, mode.annotation) != null) {
                modes.add(mode);
            }
        }
        return modes;
    }

    /**
     * Returns the mode {@code method} is checked in: its single declared mode, or {@link
     * MethodMode#ABSTRACT_STATE} if it declares none. A method that declares more than one mode is
     * reported by the visitor and checked in {@link MethodMode#ABSTRACT_STATE}.
     *
     * @param method a method
     * @return the mode {@code method} is checked in
     */
    public MethodMode getMethodMode(ExecutableElement method) {
        List<MethodMode> modes = getDeclaredMethodModes(method);
        return modes.size() == 1 ? modes.get(0) : MethodMode.ABSTRACT_STATE;
    }

    /**
     * Returns the mode of the method whose body contains {@code tree}. Lambda bodies take the mode
     * of their enclosing method. A tree outside any method, such as a field initializer, or a tree
     * in a class nested inside a method but outside that class's own methods, is in {@link
     * MethodMode#ABSTRACT_STATE}.
     *
     * @param tree a tree in the current compilation unit
     * @return the mode {@code tree} is checked in
     */
    MethodMode getMethodModeOf(Tree tree) {
        TreePath path = getPath(tree);
        if (path == null) {
            return MethodMode.ABSTRACT_STATE;
        }
        Tree boundary = TreePathUtil.enclosingOfKind(path, MODE_BOUNDARY_KINDS);
        if (!(boundary instanceof MethodTree)) {
            return MethodMode.ABSTRACT_STATE;
        }
        ExecutableElement method = TreeUtils.elementFromDeclaration((MethodTree) boundary);
        return method == null ? MethodMode.ABSTRACT_STATE : getMethodMode(method);
    }

    /**
     * Returns the mode viewpoint adaptation is currently performed in: the mode of the innermost
     * tree being typed, or {@link MethodMode#ABSTRACT_STATE} outside any tree.
     *
     * @return the current method mode
     */
    public MethodMode getCurrentMethodMode() {
        MethodMode mode = methodModeStack.peek();
        return mode == null ? MethodMode.ABSTRACT_STATE : mode;
    }

    @Override
    public AnnotatedTypeMirror getAnnotatedType(Tree tree) {
        methodModeStack.push(getMethodModeOf(tree));
        try {
            return super.getAnnotatedType(tree);
        } finally {
            methodModeStack.pop();
        }
    }

    @Override
    protected ParameterizedExecutableType methodFromUse(
            MethodInvocationTree tree, boolean inferTypeArgs) {
        methodModeStack.push(getMethodModeOf(tree));
        try {
            return super.methodFromUse(tree, inferTypeArgs);
        } finally {
            methodModeStack.pop();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The cache is keyed on the method and receiver only, so it is safe only while the adapted
     * method type does not depend on the caller's mode. {@link MethodMode#ABSTRACT_STATE} and
     * {@link MethodMode#CONCRETE_STATE} share one value adaptation and may use it; {@link
     * MethodMode#READONLY_STATE} and {@link MethodMode#TRANSITIVE_STATE} callers neither read nor
     * write it.
     */
    @Override
    protected boolean shouldCacheMethodAsMemberOf() {
        return !getCurrentMethodMode().changesValueAdaptation();
    }

    @Override
    protected ParameterizedExecutableType constructorFromUse(
            NewClassTree tree, boolean inferTypeArgs) {
        methodModeStack.push(getMethodModeOf(tree));
        try {
            return constructorFromUseInMode(tree, inferTypeArgs);
        } finally {
            methodModeStack.pop();
        }
    }

    /**
     * The mutability-specific part of {@link #constructorFromUse(NewClassTree, boolean)}, run with
     * the creation expression's mode pushed.
     *
     * @param tree the creation expression
     * @param inferTypeArgs whether to infer type arguments
     * @return the constructor type for {@code tree}
     */
    private ParameterizedExecutableType constructorFromUseInMode(
            NewClassTree tree, boolean inferTypeArgs) {
        ParameterizedExecutableType constructorType = super.constructorFromUse(tree, inferTypeArgs);
        AnnotatedExecutableType constructor = constructorType.executableType;
        if (getExplicitNewClassAnnos(tree).isEmpty()) {
            // An anonymous class carries no declaration of its own, so its bound falls back to the
            // flat default rather than the bound of the type it extends or implements. That yields
            // @Readonly for an @Immutable supertype and @Mutable for a @ReceiverDependentMutable
            // one, neither of which describes a freshly created object, and neither of which can be
            // corrected at the use site: there is nowhere to write a qualifier on `new Base() {}`.
            // Inherit the supertype's declaration bound instead.
            if (tree.getClassBody() != null) {
                AnnotationMirror superBound =
                        getQualifierHierarchy()
                                .findAnnotationInSameHierarchy(
                                        getTypeDeclarationBounds(
                                                TreeUtils.typeOf(tree.getIdentifier())),
                                        READONLY);
                if (superBound != null) {
                    constructor.getReturnType().replaceAnnotation(superBound);
                }
            }
            // For object creation, if the constructor return type is @RDM, use the default
            // concrete creation qualifier.
            if (constructor.getReturnType().hasAnnotation(RECEIVER_DEPENDENT_MUTABLE)) {
                constructor.getReturnType().replaceAnnotation(MUTABLE);
            }
        }
        return constructorType;
    }

    /**
     * {@inheritDoc} Forbid applying top annotations to type variables if they are used on local
     * variables.
     */
    @Override
    public boolean getShouldDefaultTypeVarLocals() {
        return false;
    }

    /**
     * {@inheritDoc} This also defaults field types, constructor return types, and method receiver
     * types when they are obtained from elements rather than trees.
     */
    @Override
    public void addComputedTypeAnnotations(Element elt, AnnotatedTypeMirror type) {
        if (elt != null) {
            addDefaultForField(type, elt);
            defaultConstructorReturnToClassBound(elt, type);
            defaultMethodReceiverToClassBound(elt, type);
        }
        super.addComputedTypeAnnotations(elt, type);
    }

    @Override
    public void postAsMemberOf(
            AnnotatedTypeMirror type, AnnotatedTypeMirror owner, Element element) {
        super.postAsMemberOf(type, owner, element);
        if (type instanceof AnnotatedExecutableType
                && SyntheticArrays.isArrayClone(owner, element)) {
            AnnotatedExecutableType cloneType = (AnnotatedExecutableType) type;
            assert cloneType.getReceiverType() != null;
            // clone() does not mutate its source array, so it is invocable through every
            // mutability viewpoint.
            cloneType.getReceiverType().replaceAnnotation(READONLY);
        }
    }

    /**
     * Returns whether {@code node} is an invocation of {@link Object#getClass()}.
     *
     * @param node a CFG node
     * @return whether {@code node} invokes {@code Object.getClass()}
     */
    boolean isObjectGetClassInvocation(Node node) {
        return NodeUtils.isMethodInvocation(node, objectGetClass, processingEnv);
    }

    /**
     * Returns the method type parameter bounds adapted to the viewpoint of a method invocation. The
     * executable type returned by {@link #methodFromUseWithoutTypeArgInference} does not expose the
     * adapted bounds needed to detect lost mutability, so adapt those bounds explicitly.
     *
     * <p>After <a href="https://github.com/eisop/checker-framework/pull/1850">pull request
     * #1850</a>, remove this specialized adaptation and use the bounds from the executable type.
     *
     * @param tree a method invocation
     * @return the adapted method type parameter bounds
     */
    List<AnnotatedTypeParameterBounds> methodTypeVariablesFromUse(MethodInvocationTree tree) {
        AnnotatedExecutableType invokedMethod =
                methodFromUseWithoutTypeArgInference(tree).executableType;
        List<AnnotatedTypeVariable> typeVariables = invokedMethod.getTypeVariables();
        List<AnnotatedTypeParameterBounds> bounds = new ArrayList<>(typeVariables.size());
        for (AnnotatedTypeVariable typeVariable : typeVariables) {
            bounds.add(typeVariable.getBounds());
        }

        AnnotatedTypeMirror receiverType = getReceiverType(tree);
        if (viewpointAdapter != null && receiverType != null) {
            viewpointAdapter.viewpointAdaptTypeParameterBounds(receiverType, bounds);
        }
        return bounds;
    }

    /**
     * {@inheritDoc} Mutability defaults type declaration bounds to {@link Mutable}; special cases
     * such as implicitly immutable types, arrays, and enums are handled by {@link
     * #getTypeDeclarationBounds}.
     *
     * @return mutable default type declaration bounds
     */
    @Override
    protected AnnotationMirrorSet getDefaultTypeDeclarationBounds() {
        AnnotationMirrorSet classBoundDefault =
                new AnnotationMirrorSet(super.getDefaultTypeDeclarationBounds());
        return replaceAnnotationInHierarchy(classBoundDefault, MUTABLE);
    }

    /**
     * Determines whether {@code atm}'s underlying type is implicitly immutable according to {@link
     * Immutable}'s {@link DefaultFor} metadata.
     *
     * @param atm the type to check
     * @return true if the underlying type is implicitly immutable
     */
    boolean isImplicitlyImmutableType(AnnotatedTypeMirror atm) {
        return isInTypeKindsOfDefaultForOfImmutable(atm) || isInTypesOfDefaultForOfImmutable(atm);
    }

    /**
     * Determines whether {@code atm}'s kind is listed in {@link Immutable}'s {@link DefaultFor}
     * metadata.
     *
     * @param atm the type to check
     * @return true if the type kind is implicitly immutable
     */
    private boolean isInTypeKindsOfDefaultForOfImmutable(AnnotatedTypeMirror atm) {
        DefaultFor defaultFor = Immutable.class.getAnnotation(DefaultFor.class);
        assert defaultFor != null;
        for (org.checkerframework.framework.qual.TypeKind typeKind : defaultFor.typeKinds()) {
            if (typeKind.name().equals(atm.getKind().name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether {@code atm}'s type is listed in {@link Immutable}'s {@link DefaultFor}
     * metadata.
     *
     * @param atm the type to check
     * @return true if the type is implicitly immutable
     */
    private boolean isInTypesOfDefaultForOfImmutable(AnnotatedTypeMirror atm) {
        if (atm.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DefaultFor defaultFor = Immutable.class.getAnnotation(DefaultFor.class);
        assert defaultFor != null;
        String fqn = TypesUtils.getQualifiedName((DeclaredType) atm.getUnderlyingType());
        for (Class<?> type : defaultFor.types()) {
            if (type.getCanonicalName().contentEquals(fqn)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if {@code type} has a valid mutability class declaration bound.
     *
     * @param type a class declaration type
     * @return true if the class bound is valid
     */
    boolean isValidClassBound(AnnotatedTypeMirror type) {
        return type.hasAnnotation(MUTABLE)
                || type.hasAnnotation(RECEIVER_DEPENDENT_MUTABLE)
                || type.hasAnnotation(IMMUTABLE);
    }

    @Override
    public AnnotationMirrorSet getTypeDeclarationBounds(TypeMirror type) {
        // Get the mutability bound for the type declaration
        AnnotationMirror bound = getTypeDeclarationBoundForMutability(type);
        AnnotationMirrorSet classBoundDefault = super.getTypeDeclarationBounds(type);
        if (bound != null) {
            classBoundDefault = replaceAnnotationInHierarchy(classBoundDefault, bound);
        }
        return classBoundDefault;
    }

    /**
     * Replace the annotation in the hierarchy with the given AnnotationMirrorSet.
     *
     * @param set The AnnotationMirrorSet to replace the annotation in
     * @param mirror The AnnotationMirror to replace with
     * @return The replaced AnnotationMirrorSet
     */
    private AnnotationMirrorSet replaceAnnotationInHierarchy(
            AnnotationMirrorSet set, AnnotationMirror mirror) {
        AnnotationMirrorSet result = new AnnotationMirrorSet(set);
        AnnotationMirror removeThis =
                getQualifierHierarchy().findAnnotationInSameHierarchy(set, mirror);
        result.remove(removeThis);
        result.add(mirror);
        return result;
    }

    /**
     * Returns the mutability type declaration bound implied by {@code type}.
     *
     * <p>Implicitly immutable types and enum declarations without explicit {@link Mutable} or
     * {@link ReceiverDependentMutable} bounds are bounded by {@link Immutable}. Array types are
     * bounded by {@link ReceiverDependentMutable}. Other types have no special bound here and use
     * the default declaration bound.
     *
     * @param type the type whose declaration bound is being computed
     * @return the implied declaration bound, or null if {@code type} has no special mutability
     *     bound
     */
    private AnnotationMirror getTypeDeclarationBoundForMutability(TypeMirror type) {
        if (isImplicitlyImmutableType(toAnnotatedType(type, false))) {
            return IMMUTABLE;
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return RECEIVER_DEPENDENT_MUTABLE;
        }
        if (type instanceof DeclaredType) {
            Element ele = ((DeclaredType) type).asElement();
            if (ele.getKind() == ElementKind.ENUM) {
                if (!AnnotationUtils.containsSameByName(getDeclAnnotations(ele), MUTABLE)
                        && !AnnotationUtils.containsSameByName(
                                getDeclAnnotations(ele),
                                RECEIVER_DEPENDENT_MUTABLE)) { // no decl anno
                    return IMMUTABLE;
                }
            }
        }
        return null;
    }

    /**
     * Returns the class bound of {@code element}'s enclosing type declaration.
     *
     * @param element element whose enclosing type declaration bound is needed
     * @return the enclosing type declaration bound, or null if no enclosing type was found
     */
    private AnnotatedTypeMirror getBoundTypeOfEnclosingClass(Element element) {
        TypeElement typeElement = ElementUtils.enclosingTypeElement(element);
        if (typeElement != null) {
            return getAnnotatedType(typeElement);
        }
        return null;
    }

    /**
     * Determines whether {@code variableElement} is assignable. Static non-final fields and fields
     * explicitly annotated with {@link Assignable} are assignable.
     *
     * @param variableElement the field element
     * @return true if the field is assignable
     */
    boolean isAssignableField(Element variableElement) {
        if (!(variableElement instanceof VariableElement)) {
            return false;
        }
        boolean hasExplicitAssignableAnnotation =
                getDeclAnnotation(variableElement, Assignable.class) != null;
        if (!ElementUtils.isStatic(variableElement)) {
            return hasExplicitAssignableAnnotation;
        }
        return hasExplicitAssignableAnnotation || !ElementUtils.isFinal(variableElement);
    }

    /**
     * Returns whether {@code field} has a valid assignability status. Every non-final field has
     * exactly one status by default: a static field is assignable and an instance field is
     * receiver-dependent assignable. Therefore, the only invalid combination is a final field that
     * is also explicitly {@link Assignable}.
     *
     * @param field the field element
     * @return whether the field has a valid assignability status
     */
    boolean hasValidAssignability(VariableElement field) {
        return !ElementUtils.isFinal(field) || !isAssignableField(field);
    }

    /**
     * Determines whether {@code tree} selects an assignable field.
     *
     * @param tree the field selection tree
     * @return true if the selected field is assignable
     */
    boolean isAssigningAssignableField(ExpressionTree tree) {
        Element fieldElement = TreeUtils.elementFromUse(tree);
        return fieldElement != null && isAssignableField(fieldElement);
    }

    @Override
    public AnnotatedTypeMirror getTypeOfExtendsImplements(Tree clause) {
        // Allow concrete class bounds to satisfy @RDM extends/implements clauses by adapting the
        // clause to the enclosing class's bound.
        AnnotatedTypeMirror fromTypeTree = super.getTypeOfExtendsImplements(clause);
        if (fromTypeTree.hasAnnotation(RECEIVER_DEPENDENT_MUTABLE)) {
            ClassTree enclosingClass = TreePathUtil.enclosingClass(getPath(clause));
            if (enclosingClass == null) {
                return fromTypeTree;
            } else {
                AnnotatedTypeMirror enclosing = getAnnotatedType(enclosingClass);
                AnnotationMirror mainBound = enclosing.getAnnotationInHierarchy(READONLY);
                fromTypeTree.replaceAnnotation(mainBound);
            }
        }
        return fromTypeTree;
    }

    /**
     * Add default annotation to the given AnnotatedTypeMirror for the given Element.
     *
     * @param annotatedTypeMirror the annotated type mirror to add default annotation
     * @param element the element to add default annotation
     */
    private void addDefaultForField(AnnotatedTypeMirror annotatedTypeMirror, Element element) {
        if (element == null || element.getKind() != ElementKind.FIELD) {
            return;
        }

        AnnotatedTypeMirror explicitType = fromElement(element);
        if (explicitType.hasAnnotationInHierarchy(READONLY)) {
            return;
        }

        AnnotationMirror declarationBound;
        if (explicitType instanceof AnnotatedTypeVariable) {
            // A type variable's effective qualifier comes from its annotated upper bound.
            declarationBound = explicitType.getEffectiveAnnotationInHierarchy(READONLY);
        } else {
            declarationBound =
                    getQualifierHierarchy()
                            .findAnnotationInHierarchy(
                                    getTypeDeclarationBounds(element.asType()), READONLY);
        }
        if (declarationBound != null
                && AnnotationUtils.areSame(declarationBound, RECEIVER_DEPENDENT_MUTABLE)) {
            // Static fields have no receiver through which to adapt an RDM qualifier.
            annotatedTypeMirror.replaceAnnotation(
                    ElementUtils.isStatic(element) ? MUTABLE : RECEIVER_DEPENDENT_MUTABLE);
        }
    }

    /**
     * Add class bound declaration to constructor return type if it doesn't have explicit
     * annotation.
     *
     * <p>For @Immutable class bound, add @Immutable annotation to constructor return type. For @RDM
     * class bound, add @RDM annotation to constructor return type. For @Mutable class bound,
     * add @Mutable annotation to constructor return type.
     *
     * @param element the element to add default annotation
     * @param type the type to add default annotation
     */
    private void defaultConstructorReturnToClassBound(Element element, AnnotatedTypeMirror type) {
        if (element.getKind() == ElementKind.CONSTRUCTOR
                && type instanceof AnnotatedExecutableType) {
            AnnotatedTypeMirror bound = getBoundTypeOfEnclosingClass(element);
            assert bound != null;
            ((AnnotatedExecutableType) type)
                    .getReturnType()
                    .addMissingAnnotations(
                            Collections.singletonList(bound.getAnnotationInHierarchy(READONLY)));
        }
    }

    /**
     * Add class bound declaration to method receiver type if it doesn't have explicit annotation.
     *
     * <p>For @Immutable class bound, add @Immutable annotation to method receiver type. For @RDM
     * class bound, add @RDM annotation to method receiver type. For @Mutable class bound,
     * add @Mutable annotation to method receiver type.
     *
     * @param element the element to add default annotation
     * @param type the type to add default annotation
     */
    private void defaultMethodReceiverToClassBound(Element element, AnnotatedTypeMirror type) {
        if (element.getKind() == ElementKind.METHOD
                && type instanceof AnnotatedExecutableType
                && !ElementUtils.isStatic(element)) {
            AnnotatedTypeMirror bound = getBoundTypeOfEnclosingClass(element);
            AnnotatedExecutableType methodType = (AnnotatedExecutableType) type;
            assert bound != null;
            methodType
                    .getReceiverType()
                    .addMissingAnnotations(
                            Collections.singletonList(bound.getAnnotationInHierarchy(READONLY)));
        }
    }

    /** Tree annotators for mutability-specific defaults and expression annotations. */
    private static class MutabilityPropagationTreeAnnotator extends PropagationTreeAnnotator {
        /** The mutability type factory. */
        private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

        /**
         * Create a new MutabilityPropagationTreeAnnotator.
         *
         * @param atypeFactory the type factory
         */
        private MutabilityPropagationTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
            mutabilityTypeFactory = (MutabilityNoInitAnnotatedTypeFactory) atypeFactory;
        }

        @Override
        public Void visitTypeCast(TypeCastTree node, AnnotatedTypeMirror type) {
            boolean hasExplicitAnnos = !type.getAnnotations().isEmpty();
            super.visitTypeCast(node, type);
            if (!hasExplicitAnnos
                    && type.hasAnnotation(mutabilityTypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
                type.replaceAnnotation(mutabilityTypeFactory.MUTABLE);
            }
            return null;
        }

        @Override
        public Void visitBinary(BinaryTree node, AnnotatedTypeMirror type) {
            return null;
        }
    }

    /** Applies mutability-specific tree defaults. */
    private static class MutabilityTreeAnnotator extends TreeAnnotator {
        /** The mutability type factory. */
        private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

        /**
         * Create a new MutabilityTreeAnnotator.
         *
         * @param atypeFactory the type factory
         */
        private MutabilityTreeAnnotator(AnnotatedTypeFactory atypeFactory) {
            super(atypeFactory);
            mutabilityTypeFactory = (MutabilityNoInitAnnotatedTypeFactory) atypeFactory;
        }

        @Override
        public Void visitNewArray(NewArrayTree tree, AnnotatedTypeMirror type) {
            AnnotatedTypeMirror componentType =
                    ((AnnotatedTypeMirror.AnnotatedArrayType) type).getComponentType();
            super.visitNewArray(tree, type);
            // If the array component type from treeAnnotator is @RDM, replace it with the default
            // concrete creation qualifier. This does not change explicitly annotated component
            // types.
            if (componentType.hasAnnotation(mutabilityTypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
                componentType.replaceAnnotation(mutabilityTypeFactory.MUTABLE);
            }
            return null;
        }

        /**
         * {@inheritDoc} This defaults constructor return and method receiver types to the enclosing
         * class bound when the executable is accessed as a tree.
         */
        @Override
        public Void visitMethod(MethodTree tree, AnnotatedTypeMirror p) {
            Element element = TreeUtils.elementFromDeclaration(tree);
            mutabilityTypeFactory.defaultConstructorReturnToClassBound(element, p);
            mutabilityTypeFactory.defaultMethodReceiverToClassBound(element, p);
            return super.visitMethod(tree, p);
        }

        /** {@inheritDoc} This covers the declaration of static fields */
        @Override
        public Void visitVariable(VariableTree tree, AnnotatedTypeMirror annotatedTypeMirror) {
            VariableElement element = TreeUtils.elementFromDeclaration(tree);
            mutabilityTypeFactory.addDefaultForField(annotatedTypeMirror, element);
            return super.visitVariable(tree, annotatedTypeMirror);
        }

        @Override
        public Void visitBinary(BinaryTree tree, AnnotatedTypeMirror type) {
            type.replaceAnnotation(mutabilityTypeFactory.IMMUTABLE);
            return null;
        }
    }

    /**
     * {@inheritDoc} This is for overriding the behavior of DefaultQualifierForUse and use
     * MutabilityQualifierForUseTypeAnnotator.
     *
     * @return MutabilityQualifierForUseTypeAnnotator
     */
    @Override
    protected DefaultQualifierForUseTypeAnnotator createDefaultForUseTypeAnnotator() {
        return new MutabilityQualifierForUseTypeAnnotator(this);
    }

    /** QualifierForUseTypeAnnotator */
    private static class MutabilityQualifierForUseTypeAnnotator
            extends DefaultQualifierForUseTypeAnnotator {
        /** The mutability type factory. */
        private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

        /**
         * Create a new MutabilityQualifierForUseTypeAnnotator.
         *
         * @param typeFactory the type factory
         */
        private MutabilityQualifierForUseTypeAnnotator(AnnotatedTypeFactory typeFactory) {
            super(typeFactory);
            mutabilityTypeFactory = (MutabilityNoInitAnnotatedTypeFactory) typeFactory;
        }

        @Override
        public AnnotationMirrorSet getDefaultAnnosForUses(Element element) {
            AnnotationMirrorSet defaults = super.getDefaultAnnosForUses(element);
            if (defaults.contains(mutabilityTypeFactory.MUTABLE)
                    || defaults.contains(mutabilityTypeFactory.IMMUTABLE)) {
                return defaults;
            }
            return AnnotationMirrorSet.emptySet();
        }
    }

    /**
     * Defaults enum declarations to immutable without changing other class declaration defaults.
     */
    private static class MutabilityEnumDefaultAnnotator extends TypeAnnotator {
        /** The mutability type factory. */
        private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

        /**
         * Create a new MutabilityEnumDefaultAnnotator.
         *
         * @param typeFactory the type factory
         */
        private MutabilityEnumDefaultAnnotator(AnnotatedTypeFactory typeFactory) {
            super(typeFactory);
            mutabilityTypeFactory = (MutabilityNoInitAnnotatedTypeFactory) typeFactory;
        }

        @Override
        public Void visitDeclared(AnnotatedTypeMirror.AnnotatedDeclaredType type, Void aVoid) {
            TypeElement typeElement = (TypeElement) type.getUnderlyingType().asElement();
            if (typeElement.getKind() == ElementKind.ENUM) {
                type.addMissingAnnotations(Collections.singleton(mutabilityTypeFactory.IMMUTABLE));
            }
            return super.visitDeclared(type, aVoid);
        }
    }
}
