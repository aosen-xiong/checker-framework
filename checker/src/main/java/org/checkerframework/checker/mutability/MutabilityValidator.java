package org.checkerframework.checker.mutability;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeValidator;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedArrayType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedPrimitiveType;
import org.checkerframework.javacutil.AnnotationMirrorSet;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

/** Checks mutability-specific validity rules that are not represented by ordinary subtyping. */
public class MutabilityValidator extends BaseTypeValidator {
    /** The type factory for the mutability no-initialization checker. */
    private final MutabilityNoInitAnnotatedTypeFactory mutabilityTypeFactory;

    /**
     * Create a MutabilityValidator.
     *
     * @param checker the checker
     * @param visitor the visitor
     * @param atypeFactory the type factory
     */
    public MutabilityValidator(
            BaseTypeChecker checker,
            BaseTypeVisitor<?> visitor,
            AnnotatedTypeFactory atypeFactory) {
        super(checker, visitor, atypeFactory);
        this.mutabilityTypeFactory = (MutabilityNoInitAnnotatedTypeFactory) atypeFactory;
    }

    @Override
    public Void visitDeclared(AnnotatedDeclaredType type, Tree tree) {
        checkClassBound(type, tree);
        checkStaticReceiverDependentMutableError(type, tree);
        checkImplicitlyImmutableTypeError(type, tree);
        checkFieldAssignabilityDeclaration(tree);

        return super.visitDeclared(type, tree);
    }

    @Override
    protected boolean shouldCheckTopLevelDeclaredOrPrimitiveType(
            AnnotatedTypeMirror type, Tree tree) {
        if (isReceiverDependentUseOfMutableFieldInMutableClass(type, tree)) {
            return false;
        }
        if (type.getKind() != TypeKind.DECLARED && !type.getKind().isPrimitive()) {
            return true;
        }
        // Mutability also validates local variable declarations.
        return !TreeUtils.isExpressionTree(tree) || TreeUtils.isTypeTree(tree);
    }

    /**
     * Returns true if the top-level declaration-bound check should be skipped for this type.
     *
     * <p>Mutability permits {@code @ReceiverDependentMutable} on a field with a mutable declared
     * bound inside a mutable class. Every receiver-dependent result is still within the declared
     * mutable bound, so the regular declaration-bound check would be too strict.
     *
     * @param type field type
     * @param tree tree whose type is being validated
     * @return true if this type use should skip the top-level declaration-bound check
     */
    private boolean isReceiverDependentUseOfMutableFieldInMutableClass(
            AnnotatedTypeMirror type, Tree tree) {
        if (!type.hasAnnotation(mutabilityTypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
            return false;
        }

        VariableElement field = getFieldElement(tree);
        if (field == null) {
            return false;
        }

        TypeElement enclosingClass = ElementUtils.enclosingTypeElement(field);
        if (enclosingClass == null) {
            return false;
        }

        AnnotationMirrorSet enclosingBound =
                atypeFactory.getTypeDeclarationBounds(enclosingClass.asType());
        if (!AnnotationUtils.containsSameByName(enclosingBound, mutabilityTypeFactory.MUTABLE)) {
            return false;
        }

        AnnotationMirrorSet declaredBound =
                atypeFactory.getTypeDeclarationBounds(type.getUnderlyingType());
        return AnnotationUtils.containsSameByName(declaredBound, mutabilityTypeFactory.MUTABLE);
    }

    @Override
    public Void visitArray(AnnotatedArrayType type, Tree tree) {
        checkStaticReceiverDependentMutableError(type, tree);
        // Array declaration bounds are receiver-dependent in the mutability checker, so
        // implicit-immutable validation
        // does not apply.
        return super.visitArray(type, tree);
    }

    @Override
    public Void visitPrimitive(AnnotatedPrimitiveType type, Tree tree) {
        checkImplicitlyImmutableTypeError(type, tree);
        checkFieldAssignabilityDeclaration(tree);
        return super.visitPrimitive(type, tree);
    }

    /**
     * Reports an error for receiver-dependent mutable type uses in static contexts.
     *
     * <p>A receiver-dependent class declaration may itself be static, but members and other type
     * uses in static contexts have no receiver to depend on.
     *
     * @param type the type to check
     * @param tree the tree to check
     */
    private void checkStaticReceiverDependentMutableError(AnnotatedTypeMirror type, Tree tree) {
        if (!type.isDeclaration()
                && TreePathUtil.isTreeInStaticScope(visitor.getCurrentPath())
                && !isClassBoundClause(tree)
                && type.hasAnnotation(mutabilityTypeFactory.RECEIVER_DEPENDENT_MUTABLE)) {
            reportValidityResult("static.receiverdependentmutable.forbidden", type, tree);
        }
    }

    /**
     * Returns whether {@code tree} is, or is part of, the extends clause or one of the implements
     * clauses of the enclosing class declaration.
     *
     * <p>Such a clause names a class BOUND, not a type use that needs a receiver, so
     * {@code @ReceiverDependentMutable} is legal there even though a nested class or interface is
     * implicitly static. The same holds inside the clause's type arguments, as in {@code implements
     * Set<Map.@ReceiverDependentMutable Entry<K, V>>}: there it follows the mutability of the
     * class's own instance. The validator passes the type-argument tree when it checks a type
     * argument, so a tree nested in a clause is exempt too. {@code
     * java.util.Spliterator.OfPrimitive} and {@code Collections.CheckedMap.CheckedEntrySet} are the
     * motivating cases; see {@code tests/pico-mutable-default/StaticRdmExtendsClause.java} and
     * {@code StaticRdmTypeArgumentInClause.java}.
     *
     * @param tree the tree being validated
     * @return true if {@code tree} is or is inside an extends or implements clause of the enclosing
     *     class
     */
    private boolean isClassBoundClause(Tree tree) {
        ClassTree enclosing = TreePathUtil.enclosingClass(visitor.getCurrentPath());
        if (enclosing == null) {
            return false;
        }
        if (containsTree(enclosing.getExtendsClause(), tree)) {
            return true;
        }
        for (Tree implemented : enclosing.getImplementsClause()) {
            if (containsTree(implemented, tree)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether {@code target} is {@code root} or a tree nested in it.
     *
     * @param root the tree to search, or null
     * @param target the tree to find
     * @return true if {@code target} is {@code root} or occurs inside it
     */
    private static boolean containsTree(@Nullable Tree root, Tree target) {
        if (root == null) {
            return false;
        }
        Boolean found =
                new TreeScanner<Boolean, Void>() {
                    @Override
                    @SuppressWarnings("interning:not.interned") // AST node comparison
                    public Boolean scan(Tree node, Void p) {
                        if (node == target) {
                            return true;
                        }
                        return super.scan(node, p);
                    }

                    @Override
                    public Boolean reduce(Boolean r1, Boolean r2) {
                        return Boolean.TRUE.equals(r1) || Boolean.TRUE.equals(r2);
                    }
                }.scan(root, null);
        return Boolean.TRUE.equals(found);
    }

    /**
     * Reports an error if an implicitly immutable type is used with a non-immutable qualifier.
     *
     * <p>Dataflow may refine an immutable type to {@code @Bottom}, as in {@code RefineFromNull};
     * therefore, {@code @Bottom} is also accepted.
     *
     * @param type the type to check
     * @param tree the tree to check
     */
    private void checkImplicitlyImmutableTypeError(AnnotatedTypeMirror type, Tree tree) {
        if (mutabilityTypeFactory.isImplicitlyImmutableType(type)
                && !type.hasAnnotation(mutabilityTypeFactory.IMMUTABLE)
                && !type.hasAnnotation(mutabilityTypeFactory.BOTTOM)) {
            reportInvalidAnnotationsOnUse(type, tree);
        }
    }

    /**
     * Reports an error if a class declaration uses an invalid mutability class bound.
     *
     * <p>Anonymous classes are validated through their creation expressions.
     *
     * @param type the class declaration type
     * @param tree the class declaration tree
     */
    private void checkClassBound(AnnotatedDeclaredType type, Tree tree) {
        if (!type.isDeclaration()
                || !(tree instanceof ClassTree)
                || TreeUtils.isAnonymousClass((ClassTree) tree)) {
            return;
        }

        if (!mutabilityTypeFactory.isValidClassBound(type)) {
            checker.reportError(tree, "class.bound.invalid", type);
            isValid = false;
        }
    }

    /**
     * Reports an error if a field declaration does not have exactly one assignability status.
     *
     * <p>This covers both instance fields and static fields.
     *
     * @param tree the tree to check
     */
    private void checkFieldAssignabilityDeclaration(Tree tree) {
        VariableElement field = getFieldElement(tree);
        if (field == null) {
            return;
        }
        if (!mutabilityTypeFactory.hasValidAssignability(field)) {
            checker.reportError(field, "assignability.declaration.invalid", field);
            isValid = false;
        }
    }

    /**
     * Returns the field declared by {@code tree}, or null if {@code tree} does not declare a field.
     *
     * @param tree the tree to inspect
     * @return the declared field element, or null
     */
    private VariableElement getFieldElement(Tree tree) {
        if (!(tree instanceof VariableTree)) {
            return null;
        }
        VariableElement variableElement = TreeUtils.elementFromDeclaration((VariableTree) tree);
        return variableElement.getKind() == ElementKind.FIELD ? variableElement : null;
    }
}
