package org.checkerframework.checker.mutability;

import com.google.common.collect.ImmutableSet;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;

import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.common.basetype.TypeValidator;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedExecutableType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedIntersectionType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedWildcardType;
import org.checkerframework.framework.type.AnnotatedTypeParameterBounds;
import org.checkerframework.framework.util.AnnotatedTypes;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** The visitor for the mutability type system. */
public class MutabilityNoInitVisitor extends BaseTypeVisitor<MutabilityNoInitAnnotatedTypeFactory> {
    /** Unary operators that mutate their operand. */
    private static final Set<Tree.Kind> SIDE_EFFECTING_UNARY_OPERATORS =
            ImmutableSet.of(
                    Tree.Kind.POSTFIX_INCREMENT,
                    Tree.Kind.PREFIX_INCREMENT,
                    Tree.Kind.POSTFIX_DECREMENT,
                    Tree.Kind.PREFIX_DECREMENT);

    /** Error key for {@code @MutabilityLost} in assignment targets. */
    private static final @CompilerMessageKey String LOST_LHS = "mutability.lost.lhs";

    /** Error key for {@code @MutabilityLost} in adapted parameter types. */
    private static final @CompilerMessageKey String LOST_PARAMETER = "mutability.lost.parameter";

    /** Error key for {@code @MutabilityLost} in an adapted method receiver type. */
    private static final @CompilerMessageKey String LOST_RECEIVER = "mutability.lost.receiver";

    /** Error key for {@code @MutabilityLost} in adapted type parameter bounds. */
    private static final @CompilerMessageKey String LOST_IN_BOUNDS = "mutability.lost.in.bounds";

    /** Error key for {@code @MutabilityLost} in a type argument. */
    private static final @CompilerMessageKey String LOST_TYPE_ARGUMENT =
            "mutability.lost.type.argument";

    /**
     * Error key for a receiver or parameter of an readonly-state or transitive-state method that
     * may carry {@code @Mutable}.
     */
    private static final @CompilerMessageKey String MODE_SIGNATURE_MUTABLE =
            "method.mode.signature.mutable";

    /**
     * Create a new MutabilityNoInitVisitor.
     *
     * @param checker the checker
     */
    public MutabilityNoInitVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    @Override
    protected TypeValidator createTypeValidator() {
        return new MutabilityValidator(checker, this, atypeFactory);
    }

    @Override
    protected void checkConstructorResult(
            AnnotatedExecutableType constructorType, ExecutableElement constructorElement) {
        // Mutability constructor results use the enclosing class bound rather than the hierarchy
        // top. processMethodTree validates the permitted constructor return qualifiers.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Unlike {@link BaseTypeVisitor#isValidUse(AnnotatedDeclaredType, AnnotatedDeclaredType,
     * Tree)}, which checks {@code use <: (use |> bound(C))}, mutability uses the well-formedness
     * rule {@code (q |> bound(C)) <: q}. That allows {@code @Readonly} (and other) uses of classes
     * whose declaration bound is mutable or receiver-dependent.
     */
    @Override
    public boolean isValidUse(
            AnnotatedDeclaredType declarationType, AnnotatedDeclaredType useType, Tree tree) {

        if (useType.hasAnnotation(atypeFactory.POLY_MUTABLE)
                || useType.hasAnnotation(atypeFactory.LOST)) {
            return true;
        }

        AnnotationMirrorSet adaptedBounds = atypeFactory.getTypeDeclarationBoundsFromUse(useType);
        return typeHierarchy.isSubtypeShallowEffective(adaptedBounds, useType);
    }

    @Override
    public boolean isValidUse(AnnotatedArrayType type, Tree tree) {
        // An array may be declared with any mutability qualifier.
        return true;
    }

    /**
     * Rejects {@code @MutabilityLost} in explicit or inferred type arguments. A lost qualifier
     * represents imprecision introduced by viewpoint adaptation, not a mutability with which a
     * generic type may be instantiated.
     */
    @Override
    protected boolean shouldCheckTypeArgument(
            Tree toptree,
            AnnotatedTypeParameterBounds bounds,
            AnnotatedTypeMirror typeArg,
            @Nullable Tree typeArgTree,
            CharSequence typeOrMethodName,
            Object paramName) {
        if (AnnotatedTypes.containsModifier(typeArg, atypeFactory.LOST)) {
            checker.reportError(typeArgTree == null ? toptree : typeArgTree, LOST_TYPE_ARGUMENT);
        }
        return super.shouldCheckTypeArgument(
                toptree, bounds, typeArg, typeArgTree, typeOrMethodName, paramName);
    }

    @Override
    protected boolean commonAssignmentCheck(
            Tree varTree,
            ExpressionTree valueExp,
            @CompilerMessageKey String errorKey,
            Object... extraArgs) {
        AnnotatedTypeMirror var = atypeFactory.getAnnotatedTypeLhs(varTree);
        assert var != null : "no variable found for tree: " + varTree;

        if (!validateType(varTree, var)) {
            return false;
        }

        boolean result = commonAssignmentCheck(var, valueExp, errorKey, extraArgs);
        return checkLostLhs(var, valueExp, result);
    }

    /**
     * Reports an error if {@code varType} contains {@code @MutabilityLost}.
     *
     * @param varType the assignment target type
     * @param valueExpTree the assignment value tree
     * @param result the result computed by the regular assignment check
     * @return false if {@code varType} contains {@code @MutabilityLost}; otherwise {@code result}
     */
    private boolean checkLostLhs(
            AnnotatedTypeMirror varType, ExpressionTree valueExpTree, boolean result) {
        if (AnnotatedTypes.containsModifier(varType, atypeFactory.LOST)) {
            checker.reportError(valueExpTree, LOST_LHS);
            return false;
        }
        return result;
    }

    @Override
    protected boolean commonAssignmentCheck(
            AnnotatedTypeMirror varType,
            AnnotatedTypeMirror valueType,
            Tree valueExpTree,
            @CompilerMessageKey String errorKey,
            Object... extraArgs) {
        boolean result =
                super.commonAssignmentCheck(varType, valueType, valueExpTree, errorKey, extraArgs);
        if (AnnotatedTypes.containsModifier(varType, atypeFactory.LOST)) {
            if (errorKey.equals("argument.type.incompatible")
                    || errorKey.equals("varargs.type.incompatible")) {
                checker.reportError(valueExpTree, LOST_PARAMETER);
            } else if (errorKey.equals("unary.increment.type.incompatible")
                    || errorKey.equals("unary.decrement.type.incompatible")) {
                checker.reportError(valueExpTree, LOST_LHS);
            }
            result = false;
        }
        return result;
    }

    @Override
    protected void checkConstructorInvocation(
            AnnotatedDeclaredType invocation,
            AnnotatedExecutableType constructor,
            NewClassTree newClassTree) {
        // @Readonly and @MutabilityLost describe an existing object without granting a concrete
        // creation mutability, so neither is valid on an object creation expression.
        if (invocation.hasAnnotation(atypeFactory.READONLY)
                || invocation.hasAnnotation(atypeFactory.LOST)) {
            checker.reportError(
                    newClassTree,
                    "constructor.invocation.invalid",
                    constructor.toString(),
                    invocation.getEffectiveAnnotationInHierarchy(atypeFactory.READONLY),
                    constructor.getReturnType().getAnnotationInHierarchy(atypeFactory.READONLY));
            return;
        }
        if (invocation.hasAnnotation(atypeFactory.POLY_MUTABLE)) {
            return;
        }
        super.checkConstructorInvocation(invocation, constructor, newClassTree);
    }

    @Override
    public void processMethodTree(String className, MethodTree tree) {
        ExecutableElement methodElement = TreeUtils.elementFromDeclaration(tree);
        if (methodElement != null
                && atypeFactory.getDeclaredMethodModes(methodElement).size() > 1) {
            checker.reportError(tree, "method.mode.multiple", methodElement);
        }
        AnnotatedExecutableType executableType = atypeFactory.getAnnotatedType(tree);
        if (methodElement != null
                && atypeFactory.getMethodMode(methodElement).changesValueAdaptation()) {
            checkProtectedSignature(
                    tree, atypeFactory.getMethodMode(methodElement), executableType);
        }
        // Report an error if the constructor return type is @Readonly or @PolyMutable. Validity is
        // also checked in BaseTypeValidator.
        if (TreeUtils.isConstructor(tree)) {
            AnnotatedDeclaredType constructorReturnType =
                    (AnnotatedDeclaredType) executableType.getReturnType();
            if (constructorReturnType.hasAnnotation(atypeFactory.READONLY)
                    || constructorReturnType.hasAnnotation(atypeFactory.POLY_MUTABLE)) {
                checker.reportError(tree, "constructor.return.invalid", constructorReturnType);
            }
        }

        super.processMethodTree(className, tree);
    }

    /**
     * Reports each receiver or parameter of an readonly-state or transitive-state method whose
     * declared type may carry mutable authority into the method.
     *
     * <p>This is the model's {@code MethodEntryTypesNoMutCtx}: no {@code @Mutable} anywhere in the
     * receiver or parameter types, following the declared bounds of the type variables they use.
     * The return type is not checked.
     *
     * @param tree the method declaration
     * @param mode the method's mode, readonly-state or transitive-state
     * @param executableType the declared type of the method
     */
    private void checkProtectedSignature(
            MethodTree tree, MethodMode mode, AnnotatedExecutableType executableType) {
        AnnotatedDeclaredType receiver = executableType.getReceiverType();
        if (receiver != null && mayCarryMutable(receiver, new HashSet<>(), new HashSet<>())) {
            Tree receiverTree = tree.getReceiverParameter();
            checker.reportError(
                    receiverTree != null ? receiverTree : tree,
                    MODE_SIGNATURE_MUTABLE,
                    "receiver",
                    mode,
                    receiver);
        }
        List<AnnotatedTypeMirror> parameterTypes = executableType.getParameterTypes();
        List<? extends VariableTree> parameterTrees = tree.getParameters();
        for (int i = 0; i < parameterTypes.size(); i++) {
            AnnotatedTypeMirror parameterType = parameterTypes.get(i);
            if (mayCarryMutable(parameterType, new HashSet<>(), new HashSet<>())) {
                boolean hasTree = i < parameterTrees.size();
                checker.reportError(
                        hasTree ? parameterTrees.get(i) : tree,
                        MODE_SIGNATURE_MUTABLE,
                        hasTree ? "parameter " + parameterTrees.get(i).getName() : "parameter",
                        mode,
                        parameterType);
            }
        }
    }

    /**
     * Returns true if {@code type} may carry mutable authority: {@code @Mutable} occurs in it along
     * a finite path through type arguments, array components, wildcard and intersection bounds, and
     * the declared bounds of type variables. This follows the model's {@code containsMutSearch}.
     * {@code @PolyMutable} is checked as poly, not as {@code @Mutable}.
     *
     * <p>A bare type-variable use is read through its whole upper bound, and a bound that is itself
     * a type variable fails closed. An explicitly qualified use such as {@code @Readonly T}
     * replaces the head of the bound, so only the bound's type arguments are examined. Expanding a
     * type variable a second time in the same way closes a cycle, as for {@code T extends
     * Comparable<T>}.
     *
     * @param type the type to search
     * @param fullBoundExpanded type variables whose whole bound has been expanded
     * @param boundArgumentsExpanded type variables whose bound's type arguments have been expanded
     * @return true if {@code type} may carry mutable authority
     */
    private boolean mayCarryMutable(
            AnnotatedTypeMirror type,
            Set<Element> fullBoundExpanded,
            Set<Element> boundArgumentsExpanded) {
        switch (type.getKind()) {
            case DECLARED:
                return isMutableAuthority(type.getAnnotationInHierarchy(atypeFactory.READONLY))
                        || anyMayCarryMutable(
                                ((AnnotatedDeclaredType) type).getTypeArguments(),
                                fullBoundExpanded,
                                boundArgumentsExpanded);
            case ARRAY:
                return isMutableAuthority(type.getAnnotationInHierarchy(atypeFactory.READONLY))
                        || mayCarryMutable(
                                ((AnnotatedArrayType) type).getComponentType(),
                                fullBoundExpanded,
                                boundArgumentsExpanded);
            case TYPEVAR:
                {
                    AnnotatedTypeVariable typeVariable = (AnnotatedTypeVariable) type;
                    Element variable = typeVariable.getUnderlyingType().asElement();
                    AnnotatedTypeMirror bound = typeVariable.getUpperBound();
                    AnnotationMirror useQualifier =
                            typeVariable.getAnnotationInHierarchy(atypeFactory.READONLY);
                    if (useQualifier == null) {
                        if (!fullBoundExpanded.add(variable)) {
                            return false;
                        }
                        if (bound.getKind() == TypeKind.TYPEVAR) {
                            return true;
                        }
                        return mayCarryMutable(bound, fullBoundExpanded, boundArgumentsExpanded);
                    }
                    if (isMutableAuthority(useQualifier)) {
                        return true;
                    }
                    if (!boundArgumentsExpanded.add(variable)) {
                        return false;
                    }
                    return boundArgumentsMayCarryMutable(
                            bound, fullBoundExpanded, boundArgumentsExpanded);
                }
            case WILDCARD:
                {
                    AnnotatedWildcardType wildcard = (AnnotatedWildcardType) type;
                    return mayCarryMutable(
                                    wildcard.getExtendsBound(),
                                    fullBoundExpanded,
                                    boundArgumentsExpanded)
                            || mayCarryMutable(
                                    wildcard.getSuperBound(),
                                    fullBoundExpanded,
                                    boundArgumentsExpanded);
                }
            case INTERSECTION:
                return anyMayCarryMutable(
                        ((AnnotatedIntersectionType) type).getBounds(),
                        fullBoundExpanded,
                        boundArgumentsExpanded);
            default:
                return false;
        }
    }

    /**
     * Returns true if the type arguments of a type-variable bound may carry mutable authority. The
     * bound's own head qualifier is not examined.
     *
     * @param bound the upper bound of a type variable
     * @param fullBoundExpanded type variables whose whole bound has been expanded
     * @param boundArgumentsExpanded type variables whose bound's type arguments have been expanded
     * @return true if the type arguments of {@code bound} may carry mutable authority
     */
    private boolean boundArgumentsMayCarryMutable(
            AnnotatedTypeMirror bound,
            Set<Element> fullBoundExpanded,
            Set<Element> boundArgumentsExpanded) {
        switch (bound.getKind()) {
            case DECLARED:
                return anyMayCarryMutable(
                        ((AnnotatedDeclaredType) bound).getTypeArguments(),
                        fullBoundExpanded,
                        boundArgumentsExpanded);
            case INTERSECTION:
                for (AnnotatedTypeMirror component :
                        ((AnnotatedIntersectionType) bound).getBounds()) {
                    if (boundArgumentsMayCarryMutable(
                            component, fullBoundExpanded, boundArgumentsExpanded)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Returns true if any of {@code types} may carry mutable authority.
     *
     * @param types the types to search
     * @param fullBoundExpanded type variables whose whole bound has been expanded
     * @param boundArgumentsExpanded type variables whose bound's type arguments have been expanded
     * @return true if any of {@code types} may carry mutable authority
     */
    private boolean anyMayCarryMutable(
            List<? extends AnnotatedTypeMirror> types,
            Set<Element> fullBoundExpanded,
            Set<Element> boundArgumentsExpanded) {
        for (AnnotatedTypeMirror type : types) {
            if (mayCarryMutable(type, fullBoundExpanded, boundArgumentsExpanded)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if {@code qualifier} grants mutable authority.
     *
     * @param qualifier a mutability qualifier, or null
     * @return true if {@code qualifier} is {@code @Mutable}
     */
    private boolean isMutableAuthority(@Nullable AnnotationMirror qualifier) {
        return qualifier != null && AnnotationUtils.areSame(qualifier, atypeFactory.MUTABLE);
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void p) {
        ExpressionTree variable = node.getVariable();
        // Field-write checks use the receiver type, including flow refinement. The later assignment
        // subtype check still uses the standard left-hand-side type.
        checkAssignment(node, variable);
        return super.visitAssignment(node, p);
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        ExpressionTree variable = node.getVariable();
        checkAssignment(node, variable);
        return super.visitCompoundAssignment(node, p);
    }

    @Override
    public Void visitUnary(UnaryTree node, Void p) {
        if (SIDE_EFFECTING_UNARY_OPERATORS.contains(node.getKind())) {
            ExpressionTree variable = node.getExpression();
            checkAssignment(node, variable);
        }
        return super.visitUnary(node, p);
    }

    /**
     * Checks whether a field or array assignment is allowed. A constructor, a method whose receiver
     * is under initialization, or an initializer block may assign a field of its current receiver.
     * These contexts do not permit writes through other readonly receivers or writes to array
     * elements.
     *
     * @param tree the assignment node
     * @param variable the variable in the assignment
     */
    private void checkAssignment(Tree tree, ExpressionTree variable) {
        AnnotatedTypeMirror receiverType = atypeFactory.getReceiverType(variable);
        boolean isCurrentReceiverField = isFieldOfCurrentReceiver(variable);
        MethodTree enclosingMethod = TreePathUtil.enclosingMethod(getCurrentPath());
        if (enclosingMethod != null) {
            List<? extends AnnotationMirror> receiverAnnotations =
                    getAllReceiverAnnotation(enclosingMethod);
            for (AnnotationMirror anno : receiverAnnotations) {
                if (isCurrentReceiverField
                        && atypeFactory.areSameByClass(anno, UnderInitialization.class)) {
                    // A receiver under initialization may initialize its own fields.
                    return;
                }
            }
            if (isCurrentReceiverField && TreeUtils.isConstructor(enclosingMethod)) {
                // Constructors may initialize fields of the object being constructed,
                // but ONLY if the field is declared in the same class. Reassigning inherited
                // fields is forbidden because the superclass constructor has already completed.
                VariableElement field = TreeUtils.asFieldAccess(variable);
                TypeElement fieldEnclosingClass = ElementUtils.enclosingTypeElement(field);
                TypeElement constructorEnclosingClass =
                        TreeUtils.elementFromDeclaration(
                                TreePathUtil.enclosingClass(getCurrentPath()));
                if (fieldEnclosingClass != null
                        && fieldEnclosingClass.equals(constructorEnclosingClass)) {
                    return;
                }
            }
        }
        if (isCurrentReceiverField
                && TreePathUtil.isTopLevelAssignmentInInitializerBlock(getCurrentPath())) {
            // Initializer blocks may initialize fields of their current receiver.
            return;
        }
        // Implicit-this field assignments have no receiver tree, but they still have a receiver
        // type, so use receiverType to decide whether to enforce write permissions.
        if (receiverType != null && !allowWrite(receiverType, variable)) {
            reportFieldOrArrayWriteError(tree, variable, receiverType);
        }
    }

    /**
     * Returns whether {@code variable} is a non-static field accessed through the current receiver.
     * This includes implicit field accesses and explicit {@code this.field} accesses, but excludes
     * fields accessed through aliases or enclosing instances.
     *
     * @param variable an assignment target
     * @return whether {@code variable} is a field of the current receiver
     */
    private boolean isFieldOfCurrentReceiver(ExpressionTree variable) {
        VariableElement field = TreeUtils.asFieldAccess(variable);
        if (field == null || ElementUtils.isStatic(field)) {
            return false;
        }

        if (variable instanceof MemberSelectTree) {
            ExpressionTree receiver = ((MemberSelectTree) variable).getExpression();
            return receiver instanceof IdentifierTree
                    && TreeUtils.isExplicitThisDereference(receiver);
        }

        // An implicit access uses the current receiver only when the field belongs to its type.
        // This excludes implicit accesses to fields of an enclosing instance.
        ClassTree currentClass = TreePathUtil.enclosingClass(getCurrentPath());
        TypeElement currentClassElement = TreeUtils.elementFromDeclaration(currentClass);
        TypeElement fieldOwner = ElementUtils.enclosingTypeElement(field);
        return fieldOwner != null
                && types.isSubtype(currentClassElement.asType(), fieldOwner.asType());
    }

    /**
     * Returns the raw receiver annotations on a method.
     *
     * @param tree the method tree
     * @return the list of receiver annotations
     */
    private List<? extends AnnotationMirror> getAllReceiverAnnotation(MethodTree tree) {
        com.sun.tools.javac.code.Symbol meth =
                (com.sun.tools.javac.code.Symbol) TreeUtils.elementFromDeclaration(tree);
        return meth.getRawTypeAttributes();
    }

    /**
     * Returns whether the receiver type permits writing to the selected field or array.
     *
     * @param receiverType the receiver type
     * @param variable the variable in the assignment
     * @return true if the receiver type allows writing, false otherwise
     */
    private boolean allowWrite(AnnotatedTypeMirror receiverType, ExpressionTree variable) {
        if (receiverType.hasAnnotation(atypeFactory.MUTABLE)) {
            return true;
        } else return atypeFactory.isAssigningAssignableField(variable);
    }

    /**
     * Reports a field or array write error.
     *
     * @param tree the node to report the error
     * @param variable the variable in the assignment
     * @param receiverType the receiver type
     */
    private void reportFieldOrArrayWriteError(
            Tree tree, ExpressionTree variable, AnnotatedTypeMirror receiverType) {
        if (variable instanceof MemberSelectTree) {
            checker.reportError(
                    TreeUtils.getReceiverTree(variable), "illegal.field.write", receiverType);
        } else if (variable instanceof IdentifierTree) {
            checker.reportError(tree, "illegal.field.write", receiverType);
        } else if (variable instanceof ArrayAccessTree) {
            checker.reportError(
                    ((ArrayAccessTree) variable).getExpression(),
                    "illegal.array.write",
                    receiverType);
        } else {
            throw new BugInCF("Unknown assignment variable at: ", tree);
        }
    }

    @Override
    public Void visitVariable(VariableTree node, Void p) {
        VariableElement element = TreeUtils.elementFromDeclaration(node);
        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(element);
        if (element.getKind() == ElementKind.FIELD) {
            if (type.hasAnnotation(atypeFactory.POLY_MUTABLE)) {
                checker.reportError(node, "field.polymutable.forbidden", element);
            }
        }
        return super.visitVariable(node, p);
    }

    @Override
    public Void visitNewArray(NewArrayTree tree, Void p) {
        AnnotatedTypeMirror type = atypeFactory.getAnnotatedType(tree);
        if (type.hasAnnotation(atypeFactory.READONLY) || type.hasAnnotation(atypeFactory.LOST)) {
            checker.reportError(tree, "array.new.invalid", type);
        }
        return super.visitNewArray(tree, p);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A method cannot be invoked when its adapted receiver type contains
     * {@code @MutabilityLost}. Receiver adaptation is uniform, so a {@code @MutabilityLost} call
     * site adapts a {@code @ReceiverDependentMutable} receiver to {@code @MutabilityLost}; this
     * check, like the one for parameters, is what rejects the call.
     */
    @Override
    protected void checkMethodInvocability(
            AnnotatedExecutableType method, MethodInvocationTree tree) {
        AnnotatedDeclaredType receiver = method.getReceiverType();
        if (receiver != null
                && !ElementUtils.isStatic(method.getElement())
                && AnnotatedTypes.containsModifier(receiver, atypeFactory.LOST)) {
            checker.reportError(tree, LOST_RECEIVER);
            return;
        }
        super.checkMethodInvocability(method, tree);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
        Void result = super.visitMethodInvocation(node, p);
        checkLostMethodTypeParameterBounds(node);
        return result;
    }

    /**
     * Reports an error if a method invocation viewpoint-adapts a method type parameter bound to
     * {@code @MutabilityLost}.
     *
     * @param tree the method invocation to check
     */
    private void checkLostMethodTypeParameterBounds(MethodInvocationTree tree) {
        if (TreeUtils.elementFromUse(tree) == null || shouldSkipUses(tree)) {
            return;
        }

        for (AnnotatedTypeParameterBounds bounds : atypeFactory.methodTypeVariablesFromUse(tree)) {
            if (AnnotatedTypes.containsModifier(bounds.getUpperBound(), atypeFactory.LOST)
                    || AnnotatedTypes.containsModifier(bounds.getLowerBound(), atypeFactory.LOST)) {
                checker.reportError(tree, LOST_IN_BOUNDS);
                return;
            }
        }
    }

    /**
     * Permits any mutability qualifier on an exception parameter by using the hierarchy bottom as
     * its lower bound.
     *
     * @return the mutability hierarchy bottom
     */
    @Override
    protected AnnotationMirrorSet getExceptionParameterLowerBoundAnnotations() {
        return AnnotationMirrorSet.singleton(atypeFactory.BOTTOM);
    }

    /**
     * Permits an expression with any mutability qualifier to be thrown. This must be overridden
     * separately because the framework otherwise uses the exception-parameter lower bound.
     *
     * @return the mutability hierarchy top
     */
    @Override
    protected AnnotationMirrorSet getThrowUpperBoundAnnotations() {
        return AnnotationMirrorSet.singleton(atypeFactory.READONLY);
    }

    @Override
    public void processClassTree(ClassTree tree) {
        TypeElement typeElement = TreeUtils.elementFromDeclaration(tree);
        // Anonymous classes are validated through their creation expressions.
        if (TreeUtils.isAnonymousClass(tree)) {
            super.processClassTree(tree);
            return;
        }
        AnnotatedTypeMirror bound = atypeFactory.getAnnotatedType(typeElement);
        if (!atypeFactory.isValidClassBound(bound)) {
            // Let MutabilityValidator report the invalid bound, then avoid cascading member
            // diagnostics that assume a valid class bound.
            validateType(tree, bound);
            return;
        }

        // In immutable or receiver-dependent-mutable classes, fields whose declared type bound is
        // mutable must have an explicit mutability qualifier to avoid implicit shallow
        // immutability.
        if (bound.hasAnnotation(atypeFactory.IMMUTABLE)
                || bound.hasAnnotation(atypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
            for (Tree member : tree.getMembers()) {
                if (!(member instanceof VariableTree)) {
                    continue;
                }
                VariableElement field = TreeUtils.elementFromDeclaration((VariableTree) member);
                if (ElementUtils.isStatic(field)) {
                    continue;
                }

                TypeMirror fieldType = field.asType();
                if (fieldType.getKind() == TypeKind.TYPEVAR) {
                    fieldType = TypesUtils.upperBound(fieldType);
                }
                if (!AnnotationUtils.containsSameByName(
                        atypeFactory.getTypeDeclarationBounds(fieldType), atypeFactory.MUTABLE)) {
                    continue;
                }

                // fromElement does not apply defaults, so it exposes whether the source had an
                // explicit mutability qualifier.
                AnnotatedTypeMirror explicitFieldType = atypeFactory.fromElement(field);
                if (!explicitFieldType.hasAnnotationInHierarchy(atypeFactory.READONLY)) {
                    checker.reportError(member, "implicit.shallow.immutable");
                }
            }
        }
        super.processClassTree(tree);
    }
}
