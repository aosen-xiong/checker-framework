package org.checkerframework.checker.mutability;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;

import org.checkerframework.checker.compilermsgs.qual.CompilerMessageKey;
import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Derivation;
import org.checkerframework.checker.mutability.qual.Derived;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Checks the discipline for {@link Derived} fields and {@link Derivation} methods.
 *
 * <p>The rules mirror the cache-method judgment: a method reads a derived field at most once, into
 * a local; every write to a derived field stores the result of a derivation method, or a local that
 * holds either such a result or the value read from the field; and a derivation method reads only
 * abstract state.
 */
final class MutabilityDerivedFieldChecker {

    /** The checker used to report errors. */
    private final BaseTypeChecker checker;

    /** The type factory, used to look up declaration annotations. */
    private final MutabilityNoInitAnnotatedTypeFactory atypeFactory;

    /** The derived fields with an access error, and the keys of those errors. */
    private final Map<Element, Set<String>> failedFields = new HashMap<>();

    /**
     * Creates a checker for derived fields.
     *
     * @param checker the checker used to report errors
     * @param atypeFactory the type factory
     */
    MutabilityDerivedFieldChecker(
            BaseTypeChecker checker, MutabilityNoInitAnnotatedTypeFactory atypeFactory) {
        this.checker = checker;
        this.atypeFactory = atypeFactory;
    }

    /**
     * Checks the derived fields and derivation methods declared in a class. The checks run only
     * with {@code -AcheckDerivedFields}. {@code -AinferDerived} also checks fields that look lazily
     * initialized or are marked {@code @LazyInit}, and {@code -AsuggestValueClasses} also reports
     * which immutable classes look like semantic value classes; both imply the first.
     *
     * @param tree the class to check
     */
    void checkClass(ClassTree tree) {
        boolean infer = checker.hasOption("inferDerived");
        boolean suggest = checker.hasOption("suggestValueClasses");
        if (!checker.hasOption("checkDerivedFields") && !infer && !suggest) {
            return;
        }
        for (Tree member : tree.getMembers()) {
            if (member instanceof VariableTree) {
                VariableElement field = TreeUtils.elementFromDeclaration((VariableTree) member);
                if (isDerived(field)) {
                    checkDeclaration(member, field);
                }
            }
        }
        for (Tree member : tree.getMembers()) {
            if (!(member instanceof MethodTree)) {
                continue;
            }
            MethodTree method = (MethodTree) member;
            ExecutableElement methodElement = TreeUtils.elementFromDeclaration(method);
            if (isDerivation(methodElement)) {
                if (method.getBody() != null) {
                    new DerivationScanner().scan(method.getBody(), null);
                }
            } else {
                for (ExecutableElement overridden :
                        ElementUtils.getOverriddenMethods(methodElement, atypeFactory.types)) {
                    if (isDerivation(overridden)) {
                        checker.reportError(method, "derivation.override.missing", overridden);
                        break;
                    }
                }
            }
        }
        // Private fields are accessible throughout the top-level class, so accesses are checked
        // once, for the top-level class and all its member classes.
        if (TreeUtils.elementFromDeclaration(tree).getNestingKind() == NestingKind.TOP_LEVEL) {
            Set<VariableElement> allDerivedFields = new LinkedHashSet<>();
            collectDerivedFields(tree, allDerivedFields);
            if (infer) {
                Set<VariableElement> inferred = new LinkedHashSet<>();
                Set<VariableElement> lazyInit = new LinkedHashSet<>();
                collectLazyInitFields(tree, lazyInit);
                inferred.addAll(lazyInit);
                inferDerivedFields(tree, inferred);
                inferred.removeAll(allDerivedFields);
                for (VariableElement field : inferred) {
                    Tree declaration = atypeFactory.declarationFromElement(field);
                    if (declaration != null) {
                        checker.reportWarning(
                                declaration,
                                lazyInit.contains(field)
                                        ? "derived.field.lazyinit"
                                        : "derived.field.inferred",
                                describe(field));
                    }
                }
                allDerivedFields.addAll(inferred);
            }
            currentDerivedFields = allDerivedFields;
            inlinedCallees.clear();
            if (!allDerivedFields.isEmpty()) {
                checkAccesses(tree, allDerivedFields);
            }
            if (suggest) {
                reportValueClasses(tree, allDerivedFields);
            }
        }
    }

    /**
     * Collects the fields of a class and of its member classes that carry an annotation named
     * {@code LazyInit}, such as Error Prone's.
     *
     * @param tree the class
     * @param lazyInit the set to add the fields to
     */
    private void collectLazyInitFields(ClassTree tree, Set<VariableElement> lazyInit) {
        for (Tree member : tree.getMembers()) {
            if (member instanceof ClassTree) {
                collectLazyInitFields((ClassTree) member, lazyInit);
            } else if (member instanceof VariableTree) {
                VariableElement field = TreeUtils.elementFromDeclaration((VariableTree) member);
                for (AnnotationMirror annotation : field.getAnnotationMirrors()) {
                    if (annotation
                            .getAnnotationType()
                            .asElement()
                            .getSimpleName()
                            .contentEquals("LazyInit")) {
                        lazyInit.add(field);
                    }
                }
            }
        }
    }

    /**
     * Reports an error about a derived field and records that the field failed.
     *
     * @param tree where to report the error
     * @param key the message key
     * @param field the derived field
     */
    void fieldError(Tree tree, @CompilerMessageKey String key, @Nullable Element field) {
        if (field != null) {
            failedFields.computeIfAbsent(field, k -> new LinkedHashSet<>()).add(key);
            checker.reportError(tree, key, describe(field));
        }
    }

    /**
     * Reports, for a class with an immutable class bound and for its member classes, whether the
     * class looks like a semantic value class: every assignable field is a derived field that
     * passes the access rules, and every other field has an immutable type. The type system already
     * ensures that fields that are not assignable are not written after construction.
     *
     * @param tree the class
     * @param derivedFields the derived fields of the enclosing top-level class
     */
    private void reportValueClasses(ClassTree tree, Set<VariableElement> derivedFields) {
        for (Tree member : tree.getMembers()) {
            if (member instanceof ClassTree) {
                reportValueClasses((ClassTree) member, derivedFields);
            }
        }
        TypeElement classElement = TreeUtils.elementFromDeclaration(tree);
        if (classElement.getKind() != ElementKind.CLASS
                && classElement.getKind() != ElementKind.ENUM
                && classElement.getKind() != ElementKind.RECORD) {
            return;
        }
        if (!AnnotationUtils.containsSameByName(
                atypeFactory.getTypeDeclarationBounds(classElement.asType()),
                atypeFactory.IMMUTABLE)) {
            return;
        }
        List<String> reasons = new ArrayList<>();
        List<String> derived = new ArrayList<>();
        Set<String> fieldClasses = new java.util.TreeSet<>();
        for (Tree member : tree.getMembers()) {
            if (!(member instanceof VariableTree)) {
                continue;
            }
            VariableElement field = TreeUtils.elementFromDeclaration((VariableTree) member);
            if (ElementUtils.isStatic(field)) {
                continue;
            }
            String name = field.getSimpleName().toString();
            boolean assignable =
                    atypeFactory.getDeclAnnotation(field, Assignable.class) != null
                            || (derivedFields.contains(field) && !ElementUtils.isFinal(field));
            if (assignable) {
                if (!derivedFields.contains(field)) {
                    reasons.add("assignable field " + name + " is not a derived field");
                } else if (failedFields.containsKey(field)) {
                    reasons.add("derived field " + name + " fails " + failedFields.get(field));
                } else {
                    derived.add(name);
                }
            }
            // The objects reachable through any field, abstract or derived, must be immutable
            // too, including the elements of an array and the type arguments of a container.
            if (!field.asType().getKind().isPrimitive()) {
                String mutablePart =
                        firstMutablePart(atypeFactory.getAnnotatedType(field), new HashSet<>());
                if (mutablePart != null) {
                    reasons.add("field " + name + " may refer to mutable state: " + mutablePart);
                }
            }
            TypeMirror erased = atypeFactory.types.erasure(field.asType());
            if (erased.getKind() == TypeKind.DECLARED) {
                fieldClasses.add(TypesUtils.getQualifiedName((DeclaredType) erased).toString());
            } else if (erased.getKind() == TypeKind.ARRAY) {
                fieldClasses.add("array");
            }
        }
        // A method that writes a field that is not derived changes state that the class does not
        // declare; the type system reports it when the class is type-checked.
        Set<String> writtenByMethods = new java.util.TreeSet<>();
        for (Tree member : tree.getMembers()) {
            if (member instanceof MethodTree
                    && ((MethodTree) member).getBody() != null
                    && !TreeUtils.isConstructor((MethodTree) member)) {
                LazyShape shape = new LazyShape(new HashMap<>());
                shape.scan(((MethodTree) member).getBody(), null);
                for (VariableElement written : shape.written) {
                    if (!derivedFields.contains(written)
                            && !ElementUtils.isStatic(written)
                            && atypeFactory.types.isSubtype(
                                    atypeFactory.types.erasure(classElement.asType()),
                                    atypeFactory.types.erasure(
                                            written.getEnclosingElement().asType()))) {
                        writtenByMethods.add(written.getSimpleName().toString());
                    }
                }
            }
        }
        if (!writtenByMethods.isEmpty()) {
            reasons.add("methods write fields that are not derived: " + writtenByMethods);
        }
        TypeMirror superclass = classElement.getSuperclass();
        if (superclass.getKind() == TypeKind.DECLARED) {
            fieldClasses.add(
                    TypesUtils.getQualifiedName(
                                    (DeclaredType) atypeFactory.types.erasure(superclass))
                            .toString());
        }
        String className = classElement.getQualifiedName().toString();
        if (reasons.isEmpty()) {
            checker.reportWarning(tree, "value.class", className, derived, fieldClasses);
        } else {
            checker.reportWarning(tree, "value.class.not", className, reasons, fieldClasses);
        }
    }

    /**
     * Finds a part of a type that is not immutable: the type itself, the component type of an
     * array, or a type argument, at any depth. A receiver-dependent qualifier counts as immutable,
     * because the enclosing class is immutable.
     *
     * @param type the annotated type of a field
     * @param visited the type variables already examined, to stop at recursive bounds
     * @return a description of the part that may be mutable, or null if every part is immutable
     */
    private @Nullable String firstMutablePart(AnnotatedTypeMirror type, Set<TypeMirror> visited) {
        if (type.getKind().isPrimitive()) {
            return null;
        }
        AnnotationMirror qualifier = type.getEffectiveAnnotationInHierarchy(atypeFactory.READONLY);
        if (qualifier == null
                || !(AnnotationUtils.areSameByName(qualifier, atypeFactory.IMMUTABLE)
                        || AnnotationUtils.areSameByName(
                                qualifier, atypeFactory.RECEIVER_DEPENDENT_MUTABLE)
                        || AnnotationUtils.areSameByName(qualifier, atypeFactory.BOTTOM))) {
            return type.getUnderlyingType()
                    + " is "
                    + (qualifier == null
                            ? "unqualified"
                            : qualifier.getAnnotationType().asElement().getSimpleName());
        }
        if (type instanceof AnnotatedTypeMirror.AnnotatedArrayType) {
            return firstMutablePart(
                    ((AnnotatedTypeMirror.AnnotatedArrayType) type).getComponentType(), visited);
        }
        if (type instanceof AnnotatedTypeMirror.AnnotatedDeclaredType) {
            for (AnnotatedTypeMirror argument :
                    ((AnnotatedTypeMirror.AnnotatedDeclaredType) type).getTypeArguments()) {
                if (visited.add(argument.getUnderlyingType())) {
                    String part = firstMutablePart(argument, visited);
                    if (part != null) {
                        return part;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the name of a field, qualified by the name of its class.
     *
     * @param field a field
     * @return the name of the field for use in messages
     */
    private static String describe(Element field) {
        return ElementUtils.getQualifiedClassName(field) + "." + field.getSimpleName();
    }

    /**
     * Finds the fields that look lazily initialized: instance fields that are not final and that
     * some method both compares with a literal, directly or through a local copy, and writes.
     *
     * @param tree the class whose methods, and those of its member classes, are examined
     * @param inferred the set to add the fields to
     */
    private void inferDerivedFields(ClassTree tree, Set<VariableElement> inferred) {
        Element classElement = TreeUtils.elementFromDeclaration(tree);
        for (Tree member : tree.getMembers()) {
            if (member instanceof ClassTree) {
                inferDerivedFields((ClassTree) member, inferred);
            } else if (member instanceof MethodTree) {
                MethodTree method = (MethodTree) member;
                if (method.getBody() == null) {
                    continue;
                }
                LocalDefinitions defs = new LocalDefinitions();
                defs.scan(method.getBody(), null);
                LazyShape shape = new LazyShape(defs.definitions);
                shape.scan(method.getBody(), null);
                for (VariableElement field : shape.written) {
                    if (shape.tested.contains(field)
                            && !ElementUtils.isStatic(field)
                            && !ElementUtils.isFinal(field)
                            && !(TreeUtils.isConstructor(method)
                                    && field.getEnclosingElement().equals(classElement))) {
                        inferred.add(field);
                    }
                }
            }
        }
    }

    /** Collects the fields of this that a method writes and those it compares with a literal. */
    private static final class LazyShape extends TreeScanner<Void, Void> {

        /** The expressions assigned to each local variable of the method. */
        private final Map<Element, List<ExpressionTree>> definitions;

        /** The fields of this that the method assigns. */
        final Set<VariableElement> written = new LinkedHashSet<>();

        /** The fields of this that the method compares with a literal. */
        final Set<VariableElement> tested = new HashSet<>();

        /**
         * Creates a scanner for one method body.
         *
         * @param definitions the expressions assigned to each local variable of the method
         */
        LazyShape(Map<Element, List<ExpressionTree>> definitions) {
            this.definitions = definitions;
        }

        @Override
        public Void visitAssignment(AssignmentTree tree, Void p) {
            VariableElement field = fieldOfThis(tree.getVariable());
            if (field != null) {
                written.add(field);
            }
            return super.visitAssignment(tree, p);
        }

        @Override
        public Void visitBinary(BinaryTree tree, Void p) {
            switch (tree.getKind()) {
                case EQUAL_TO:
                case NOT_EQUAL_TO:
                case LESS_THAN:
                case LESS_THAN_EQUAL:
                case GREATER_THAN:
                case GREATER_THAN_EQUAL:
                    if (isLiteral(tree.getRightOperand())) {
                        recordTested(tree.getLeftOperand());
                    } else if (isLiteral(tree.getLeftOperand())) {
                        recordTested(tree.getRightOperand());
                    }
                    break;
                default:
                    break;
            }
            return super.visitBinary(tree, p);
        }

        /**
         * Returns true if the expression is a literal, possibly negated.
         *
         * @param tree an expression
         * @return true if the expression is a literal
         */
        private static boolean isLiteral(ExpressionTree tree) {
            ExpressionTree expr = TreeUtils.withoutParens(tree);
            if (expr instanceof UnaryTree && expr.getKind() == Tree.Kind.UNARY_MINUS) {
                expr = TreeUtils.withoutParens(((UnaryTree) expr).getExpression());
            }
            return expr instanceof LiteralTree;
        }

        /**
         * Records the field that a compared expression holds: the field itself, an assignment of
         * the field, or a local variable that is assigned the field.
         *
         * @param tree the expression compared with a literal
         */
        private void recordTested(ExpressionTree tree) {
            ExpressionTree expr = TreeUtils.withoutParensOrCasts(tree);
            if (expr instanceof AssignmentTree) {
                recordTested(((AssignmentTree) expr).getExpression());
                return;
            }
            VariableElement field = fieldOfThis(expr);
            if (field != null) {
                tested.add(field);
                return;
            }
            if (expr instanceof IdentifierTree) {
                List<ExpressionTree> defs = definitions.get(TreeUtils.elementFromTree(expr));
                if (defs != null) {
                    for (ExpressionTree def : defs) {
                        VariableElement copied = fieldOfThis(TreeUtils.withoutParensOrCasts(def));
                        if (copied != null) {
                            tested.add(copied);
                        }
                    }
                }
            }
        }
    }

    /**
     * Collects the derived fields declared in a class and in its member classes.
     *
     * @param tree the class
     * @param derivedFields the set to add the derived fields to
     */
    private void collectDerivedFields(ClassTree tree, Set<VariableElement> derivedFields) {
        for (Tree member : tree.getMembers()) {
            if (member instanceof ClassTree) {
                collectDerivedFields((ClassTree) member, derivedFields);
            } else if (member instanceof VariableTree) {
                VariableElement field = TreeUtils.elementFromDeclaration((VariableTree) member);
                if (isDerived(field)) {
                    derivedFields.add(field);
                }
            }
        }
    }

    /**
     * Checks the accesses to derived fields in the methods of a class and of its member classes.
     *
     * @param tree the class whose methods are checked
     * @param derivedFields the derived fields of the enclosing top-level class
     */
    private void checkAccesses(ClassTree tree, Set<VariableElement> derivedFields) {
        Element classElement = TreeUtils.elementFromDeclaration(tree);
        for (Tree member : tree.getMembers()) {
            if (member instanceof ClassTree) {
                checkAccesses((ClassTree) member, derivedFields);
            } else if (member instanceof MethodTree) {
                MethodTree method = (MethodTree) member;
                if (method.getBody() == null) {
                    continue;
                }
                Set<VariableElement> checkedFields = derivedFields;
                if (TreeUtils.isConstructor(method)) {
                    // A constructor initializes the fields of its own class before the object is
                    // shared.
                    checkedFields = new LinkedHashSet<>(derivedFields);
                    checkedFields.removeIf(
                            field -> field.getEnclosingElement().equals(classElement));
                    if (checkedFields.isEmpty()) {
                        continue;
                    }
                }
                MutabilityDerivedFieldFlow flow =
                        new MutabilityDerivedFieldFlow(
                                this, checkedFields, checker.hasOption("allowReadAfterOwnWrite"));
                List<ClassTree> localClasses;
                try {
                    localClasses =
                            flow.checkMethod(
                                    checker.getPathToCompilationUnit().getCompilationUnit(),
                                    tree,
                                    method,
                                    checker.getProcessingEnvironment());
                } catch (RuntimeException e) {
                    checker.reportWarning(method, "derived.field.analysis.failed", e.toString());
                    continue;
                }
                for (ClassTree localClass : localClasses) {
                    checkAccesses(localClass, derivedFields);
                }
            }
        }
    }

    /**
     * Returns true if the field is annotated with {@link Derived}.
     *
     * @param field a field
     * @return true if the field is derived state
     */
    private boolean isDerived(Element field) {
        return atypeFactory.getDeclAnnotation(field, Derived.class) != null;
    }

    /**
     * Returns true if the method is annotated with {@link Derivation}.
     *
     * @param method a method
     * @return true if the method is a derivation method
     */
    boolean isDerivation(ExecutableElement method) {
        return atypeFactory.getDeclAnnotation(method, Derivation.class) != null;
    }

    /**
     * Checks the declaration of a derived field.
     *
     * @param tree the field declaration
     * @param field the field
     */
    private void checkDeclaration(Tree tree, VariableElement field) {
        if (atypeFactory.getDeclAnnotation(field, Assignable.class) == null) {
            checker.reportError(tree, "derived.field.not.assignable", describe(field));
        }
        if (ElementUtils.isStatic(field)) {
            checker.reportError(tree, "derived.field.static", describe(field));
        }
        if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            checker.reportError(tree, "derived.field.not.private", describe(field));
        }
        TypeKind kind = field.asType().getKind();
        if ((kind == TypeKind.LONG || kind == TypeKind.DOUBLE)
                && !field.getModifiers().contains(Modifier.VOLATILE)) {
            checker.reportError(tree, "derived.field.wide", describe(field));
        }
    }

    /**
     * Returns the field that an expression accesses through {@code this}, or null.
     *
     * @param tree an expression
     * @return the field accessed as {@code f} or {@code this.f}, or null
     */
    private static @Nullable VariableElement fieldOfThis(ExpressionTree tree) {
        ExpressionTree expr = TreeUtils.withoutParens(tree);
        if (expr instanceof MemberSelectTree) {
            ExpressionTree receiver = ((MemberSelectTree) expr).getExpression();
            if (!(receiver instanceof IdentifierTree)
                    || !((IdentifierTree) receiver).getName().contentEquals("this")) {
                return null;
            }
        } else if (!(expr instanceof IdentifierTree)) {
            return null;
        }
        Element element = TreeUtils.elementFromTree(expr);
        if (element != null && element.getKind() == ElementKind.FIELD) {
            return (VariableElement) element;
        }
        return null;
    }

    /** Collects, for each local variable of a method, the expressions assigned to it. */
    private static final class LocalDefinitions extends TreeScanner<Void, Void> {

        /** Maps each local variable to the expressions assigned to it. */
        final Map<Element, List<ExpressionTree>> definitions = new HashMap<>();

        @Override
        public Void visitVariable(VariableTree tree, Void p) {
            Element element = TreeUtils.elementFromDeclaration(tree);
            if (element.getKind() == ElementKind.LOCAL_VARIABLE && tree.getInitializer() != null) {
                definitions
                        .computeIfAbsent(element, k -> new ArrayList<>())
                        .add(tree.getInitializer());
            }
            return super.visitVariable(tree, p);
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void p) {
            Element element = TreeUtils.elementFromDeclaration(tree.getVariable());
            definitions.computeIfAbsent(element, k -> new ArrayList<>()).add(tree.getExpression());
            return super.visitEnhancedForLoop(tree, p);
        }

        @Override
        public Void visitAssignment(AssignmentTree tree, Void p) {
            ExpressionTree target = TreeUtils.withoutParens(tree.getVariable());
            if (target instanceof IdentifierTree) {
                Element element = TreeUtils.elementFromTree(target);
                if (element != null && element.getKind() == ElementKind.LOCAL_VARIABLE) {
                    definitions
                            .computeIfAbsent(element, k -> new ArrayList<>())
                            .add(tree.getExpression());
                }
            }
            return super.visitAssignment(tree, p);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void p) {
            recordUpdate(tree.getVariable(), tree);
            return super.visitCompoundAssignment(tree, p);
        }

        @Override
        public Void visitUnary(UnaryTree tree, Void p) {
            switch (tree.getKind()) {
                case PREFIX_INCREMENT:
                case PREFIX_DECREMENT:
                case POSTFIX_INCREMENT:
                case POSTFIX_DECREMENT:
                    recordUpdate(tree.getExpression(), tree);
                    break;
                default:
                    break;
            }
            return super.visitUnary(tree, p);
        }

        /**
         * Records an in-place update of a local variable. The update expression is never a derived
         * value, so a local updated in place cannot be stored in a derived field.
         *
         * @param target the updated expression
         * @param update the compound assignment or increment
         */
        private void recordUpdate(ExpressionTree target, ExpressionTree update) {
            ExpressionTree expr = TreeUtils.withoutParens(target);
            if (expr instanceof IdentifierTree) {
                Element element = TreeUtils.elementFromTree(expr);
                if (element != null && element.getKind() == ElementKind.LOCAL_VARIABLE) {
                    definitions.computeIfAbsent(element, k -> new ArrayList<>()).add(update);
                }
            }
        }
    }

    /** The derived fields of the top-level class being checked. */
    private Set<VariableElement> currentDerivedFields = new LinkedHashSet<>();

    /** The result of checking the body of each callee that is inlined into a derivation. */
    private final Map<ExecutableElement, InlinedCallee> inlinedCallees = new HashMap<>();

    /** What is known about a callee whose body is treated as part of the calling expression. */
    static final class InlinedCallee {

        /** Whether the body has been attributed by the compiler, so that it can be examined. */
        boolean available = true;

        /** Whether the body reads only abstract state and writes no field. */
        boolean clean = true;

        /** The calls in the body that are not known to be side-effect free. */
        final Set<MethodInvocationTree> impureCalls = new LinkedHashSet<>();
    }

    /** The sentinel of a derived field of a reference type. */
    static final Object NULL_SENTINEL = new Object();

    /**
     * Returns the sentinel of a derived field: the value it holds until a derived value is stored.
     * This is the literal the field is initialized with, or the default value of its type.
     *
     * @param field a derived field
     * @return a {@code Long} for an integral field, a {@code Boolean} for a boolean field, {@link
     *     #NULL_SENTINEL} for a reference field, or null if the sentinel is not known
     */
    @Nullable Object sentinelOf(VariableElement field) {
        Tree declaration = atypeFactory.declarationFromElement(field);
        ExpressionTree initializer =
                declaration instanceof VariableTree
                        ? ((VariableTree) declaration).getInitializer()
                        : null;
        if (initializer != null) {
            ExpressionTree expr = TreeUtils.withoutParens(initializer);
            long sign = 1;
            if (expr.getKind() == Tree.Kind.UNARY_MINUS) {
                expr = TreeUtils.withoutParens(((UnaryTree) expr).getExpression());
                sign = -1;
            }
            Object value;
            if (expr instanceof LiteralTree) {
                value = ((LiteralTree) expr).getValue();
            } else {
                // A named compile-time constant, such as a sentinel declared static final.
                Element constant = TreeUtils.elementFromTree(expr);
                value =
                        constant instanceof VariableElement
                                ? ((VariableElement) constant).getConstantValue()
                                : null;
                if (value == null) {
                    return null;
                }
            }
            if (value == null) {
                return NULL_SENTINEL;
            }
            if (value instanceof Integer || value instanceof Long) {
                return sign * ((Number) value).longValue();
            }
            if (value instanceof Boolean) {
                return value;
            }
            return null;
        }
        switch (field.asType().getKind()) {
            case BYTE:
            case SHORT:
            case INT:
            case LONG:
            case CHAR:
                return 0L;
            case BOOLEAN:
                return false;
            case FLOAT:
            case DOUBLE:
                return null;
            default:
                return NULL_SENTINEL;
        }
    }

    /**
     * Returns the name of the derivation method that a derived field requires.
     *
     * @param field a derived field
     * @return the name given by the field's annotation, or the empty string
     */
    String derivationOf(VariableElement field) {
        Derived annotation = field.getAnnotation(Derived.class);
        return annotation == null ? "" : annotation.by();
    }

    /**
     * Returns true if a field is declared assignable.
     *
     * @param field a field
     * @return true if the field is assignable
     */
    boolean isAssignable(VariableElement field) {
        return atypeFactory.getDeclAnnotation(field, Assignable.class) != null;
    }

    /**
     * Returns true if a method is side-effect free, or assumed to be.
     *
     * @param method a method
     * @return true if the method is side-effect free
     */
    boolean isSideEffectFree(ExecutableElement method) {
        return atypeFactory.isSideEffectFree(method);
    }

    /**
     * Reports that the value written to a derived field calls a method that is not known to be
     * side-effect free.
     *
     * @param call the call
     * @param field the derived field
     */
    void impureCallError(MethodInvocationTree call, VariableElement field) {
        failedFields
                .computeIfAbsent(field, k -> new LinkedHashSet<>())
                .add("derived.field.write.impure");
        checker.reportError(
                call,
                "derived.field.write.impure",
                describe(field),
                TreeUtils.elementFromUse(call));
    }

    /**
     * Checks the body of a callee as part of the expression that calls it. This applies to a method
     * whose source is being compiled and that cannot be overridden, so that its body is the code
     * that runs.
     *
     * @param callee a method called on this, or a static method
     * @return what is known about the body, or null if the body is not available
     */
    @Nullable InlinedCallee inlineCallee(ExecutableElement callee) {
        InlinedCallee known = inlinedCallees.get(callee);
        if (known != null) {
            return known.available ? known : null;
        }
        Set<Modifier> modifiers = callee.getModifiers();
        boolean fixed =
                modifiers.contains(Modifier.PRIVATE)
                        || modifiers.contains(Modifier.STATIC)
                        || modifiers.contains(Modifier.FINAL)
                        || callee.getEnclosingElement().getModifiers().contains(Modifier.FINAL);
        if (!fixed) {
            return null;
        }
        Tree declaration = atypeFactory.declarationFromElement(callee);
        if (!(declaration instanceof MethodTree) || ((MethodTree) declaration).getBody() == null) {
            return null;
        }
        InlinedCallee result = new InlinedCallee();
        // Registered before the scan so that a recursive call finds it.
        inlinedCallees.put(callee, result);
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree tree, Void p) {
                checkUse(tree);
                return super.visitIdentifier(tree, p);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree tree, Void p) {
                checkUse(tree);
                return super.visitMemberSelect(tree, p);
            }

            @Override
            public Void visitAssignment(AssignmentTree tree, Void p) {
                checkNoFieldWrite(tree.getVariable());
                return super.visitAssignment(tree, p);
            }

            @Override
            public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void p) {
                checkNoFieldWrite(tree.getVariable());
                return super.visitCompoundAssignment(tree, p);
            }

            @Override
            public Void visitUnary(UnaryTree tree, Void p) {
                switch (tree.getKind()) {
                    case PREFIX_INCREMENT:
                    case PREFIX_DECREMENT:
                    case POSTFIX_INCREMENT:
                    case POSTFIX_DECREMENT:
                        checkNoFieldWrite(tree.getExpression());
                        break;
                    default:
                        break;
                }
                return super.visitUnary(tree, p);
            }

            @Override
            public Void visitMethodInvocation(MethodInvocationTree tree, Void p) {
                Element innerElement = TreeUtils.elementFromTree((Tree) tree);
                if (!(innerElement instanceof ExecutableElement)) {
                    // The body belongs to a class that the compiler has not attributed yet.
                    result.available = false;
                    return null;
                }
                ExecutableElement inner = (ExecutableElement) innerElement;
                if (!isDerivation(inner)) {
                    ExpressionTree receiver = TreeUtils.getReceiverTree(tree);
                    boolean onThis =
                            receiver == null
                                    || (TreeUtils.withoutParens(receiver) instanceof IdentifierTree
                                            && (((IdentifierTree) TreeUtils.withoutParens(receiver))
                                                            .getName()
                                                            .contentEquals("this")
                                                    || ((IdentifierTree)
                                                                    TreeUtils.withoutParens(
                                                                            receiver))
                                                            .getName()
                                                            .contentEquals("super")));
                    InlinedCallee nested = onThis ? inlineCallee(inner) : null;
                    if (nested != null) {
                        result.clean &= nested.clean;
                        result.impureCalls.addAll(nested.impureCalls);
                    } else if (!atypeFactory.isSideEffectFree(inner)) {
                        result.impureCalls.add(tree);
                    }
                }
                return super.visitMethodInvocation(tree, p);
            }

            private void checkUse(ExpressionTree tree) {
                Element element = TreeUtils.elementFromTree(tree);
                if (element == null) {
                    result.available = false;
                }
                if (element != null
                        && element.getKind() == ElementKind.FIELD
                        && (atypeFactory.getDeclAnnotation(element, Assignable.class) != null
                                || currentDerivedFields.contains(element))) {
                    result.clean = false;
                }
            }

            private void checkNoFieldWrite(ExpressionTree target) {
                ExpressionTree expr = TreeUtils.withoutParens(target);
                if (expr instanceof IdentifierTree || expr instanceof MemberSelectTree) {
                    Element element = TreeUtils.elementFromTree(expr);
                    if (element != null && element.getKind() == ElementKind.FIELD) {
                        result.clean = false;
                    }
                }
            }
        }.scan(((MethodTree) declaration).getBody(), null);
        return result.available ? result : null;
    }

    /** Checks that the body of a derivation method reads only abstract state. */
    private final class DerivationScanner extends TreeScanner<Void, Void> {

        @Override
        public Void visitAssignment(AssignmentTree tree, Void p) {
            if (reportFieldWrite(tree, tree.getVariable())) {
                return scan(tree.getExpression(), p);
            }
            return super.visitAssignment(tree, p);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree tree, Void p) {
            if (reportFieldWrite(tree, tree.getVariable())) {
                return scan(tree.getExpression(), p);
            }
            return super.visitCompoundAssignment(tree, p);
        }

        @Override
        public Void visitUnary(UnaryTree tree, Void p) {
            switch (tree.getKind()) {
                case PREFIX_INCREMENT:
                case PREFIX_DECREMENT:
                case POSTFIX_INCREMENT:
                case POSTFIX_DECREMENT:
                    if (reportFieldWrite(tree, tree.getExpression())) {
                        return null;
                    }
                    break;
                default:
                    break;
            }
            return super.visitUnary(tree, p);
        }

        @Override
        public Void visitIdentifier(IdentifierTree tree, Void p) {
            reportAssignableRead(tree);
            return super.visitIdentifier(tree, p);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree tree, Void p) {
            reportAssignableRead(tree);
            return super.visitMemberSelect(tree, p);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void p) {
            ExecutableElement callee = TreeUtils.elementFromUse(tree);
            if (!isDerivation(callee) && !atypeFactory.isSideEffectFree(callee)) {
                checker.reportError(tree, "derivation.call.impure", callee);
            }
            return super.visitMethodInvocation(tree, p);
        }

        /**
         * Reports a write whose target is a field.
         *
         * @param tree the assignment
         * @param target the assigned expression
         * @return true if the target is a field and an error was reported
         */
        private boolean reportFieldWrite(Tree tree, ExpressionTree target) {
            ExpressionTree expr = TreeUtils.withoutParens(target);
            if (expr instanceof IdentifierTree || expr instanceof MemberSelectTree) {
                Element element = TreeUtils.elementFromTree(expr);
                if (element != null && element.getKind() == ElementKind.FIELD) {
                    checker.reportError(tree, "derivation.field.write", element);
                    return true;
                }
            }
            return false;
        }

        /**
         * Reports a read of an assignable field.
         *
         * @param tree an identifier or member select
         */
        private void reportAssignableRead(ExpressionTree tree) {
            Element element = TreeUtils.elementFromTree(tree);
            if (element != null
                    && element.getKind() == ElementKind.FIELD
                    && atypeFactory.getDeclAnnotation(element, Assignable.class) != null) {
                checker.reportError(tree, "derivation.reads.assignable", element);
            }
        }
    }
}
