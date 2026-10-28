package org.checkerframework.checker.mutability;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.analysis.AbstractValue;
import org.checkerframework.dataflow.analysis.AnalysisResult;
import org.checkerframework.dataflow.analysis.ConditionalTransferResult;
import org.checkerframework.dataflow.analysis.ForwardAnalysisImpl;
import org.checkerframework.dataflow.analysis.ForwardTransferFunction;
import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.Store;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.builder.CFGBuilder;
import org.checkerframework.dataflow.cfg.node.AbstractNodeVisitor;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.ConditionalAndNode;
import org.checkerframework.dataflow.cfg.node.ConditionalNotNode;
import org.checkerframework.dataflow.cfg.node.ConditionalOrNode;
import org.checkerframework.dataflow.cfg.node.EqualToNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.IntegerLiteralNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.LongLiteralNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.NarrowingConversionNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.NotEqualNode;
import org.checkerframework.dataflow.cfg.node.NullLiteralNode;
import org.checkerframework.dataflow.cfg.node.NumericalAdditionNode;
import org.checkerframework.dataflow.cfg.node.NumericalMinusNode;
import org.checkerframework.dataflow.cfg.node.NumericalSubtractionNode;
import org.checkerframework.dataflow.cfg.node.ObjectCreationNode;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.dataflow.cfg.node.StringConversionNode;
import org.checkerframework.dataflow.cfg.node.TernaryExpressionNode;
import org.checkerframework.dataflow.cfg.node.ThisNode;
import org.checkerframework.dataflow.cfg.node.TypeCastNode;
import org.checkerframework.dataflow.cfg.node.ValueLiteralNode;
import org.checkerframework.dataflow.cfg.node.WideningConversionNode;
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer;
import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.javacutil.ElementUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;

/**
 * A flow-sensitive analysis of the accesses to derived fields in one method.
 *
 * <p>For every program point it tracks, for each derived field of {@code this}, whether the field
 * may already have been read and whether it has certainly been written on the way, and, for each
 * local variable, where its value comes from: a read of a derived field, a call of a derivation
 * method, another computation over abstract state, or something that is not derived.
 */
final class MutabilityDerivedFieldFlow {

    /** The checker that owns this analysis and reports its findings. */
    private final MutabilityDerivedFieldChecker owner;

    /** The derived fields whose accesses are checked. */
    private final Set<VariableElement> derivedFields;

    /** Whether a read that follows a write by the same method is allowed. */
    private final boolean allowReadAfterOwnWrite;

    /** The field accesses that occur under a lock. */
    private final Set<Tree> synchronizedAccesses = new HashSet<>();

    /** The trees for which an error has been reported. */
    private final Set<Tree> reported = new HashSet<>();

    /**
     * Creates an analysis.
     *
     * @param owner the checker that reports the findings
     * @param derivedFields the derived fields whose accesses are checked
     * @param allowReadAfterOwnWrite whether a read that follows a write by the same method is
     *     allowed
     */
    MutabilityDerivedFieldFlow(
            MutabilityDerivedFieldChecker owner,
            Set<VariableElement> derivedFields,
            boolean allowReadAfterOwnWrite) {
        this.owner = owner;
        this.derivedFields = derivedFields;
        this.allowReadAfterOwnWrite = allowReadAfterOwnWrite;
    }

    /**
     * Checks one method, and the lambdas it contains.
     *
     * @param root the compilation unit
     * @param classTree the class that contains the method
     * @param method the method
     * @param env the processing environment
     * @return the classes declared inside the method, whose methods the caller checks in turn
     */
    List<ClassTree> checkMethod(
            CompilationUnitTree root,
            ClassTree classTree,
            MethodTree method,
            ProcessingEnvironment env) {
        boolean synchronizedMethod =
                method.getModifiers().getFlags().contains(Modifier.SYNCHRONIZED);
        new TreeScanner<Void, Boolean>() {
            @Override
            public Void visitSynchronized(SynchronizedTree tree, Boolean locked) {
                return super.visitSynchronized(tree, true);
            }

            @Override
            public Void visitIdentifier(IdentifierTree tree, Boolean locked) {
                if (locked) {
                    synchronizedAccesses.add(tree);
                }
                return super.visitIdentifier(tree, locked);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree tree, Boolean locked) {
                if (locked) {
                    synchronizedAccesses.add(tree);
                }
                return super.visitMemberSelect(tree, locked);
            }
        }.scan(method.getBody(), synchronizedMethod);

        ControlFlowGraph cfg =
                CFGBuilder.build(root, new UnderlyingAST.CFGMethod(method, classTree), env);
        check(cfg);
        for (LambdaExpressionTree lambda : cfg.getDeclaredLambdas()) {
            check(
                    CFGBuilder.build(
                            root, new UnderlyingAST.CFGLambda(lambda, classTree, method), env));
        }
        return cfg.getDeclaredClasses();
    }

    /**
     * Runs the analysis on a control flow graph and reports the findings.
     *
     * @param cfg the control flow graph of a method or of a lambda
     */
    private void check(ControlFlowGraph cfg) {
        ForwardAnalysisImpl<Origin, FlowStore, Transfer> analysis =
                new ForwardAnalysisImpl<>(new Transfer());
        analysis.performAnalysis(cfg);
        AnalysisResult<Origin, FlowStore> result = analysis.getResult();
        for (Node node : cfg.getAllNodes()) {
            if (node instanceof FieldAccessNode) {
                checkRead((FieldAccessNode) node, result);
            } else if (node instanceof AssignmentNode) {
                checkWrite((AssignmentNode) node, result);
            } else if (node instanceof ReturnNode) {
                checkReturn((ReturnNode) node, result);
            }
        }
    }

    /**
     * Reports a returned value that depends on whether a derived field has been computed: a value
     * read from the field that has not been tested against the field's initial value, or a value
     * that is chosen by a test of the field but is not the field's derived value.
     *
     * @param node a return statement
     * @param result the result of the analysis
     */
    private void checkReturn(ReturnNode node, AnalysisResult<Origin, FlowStore> result) {
        Node returned = node.getResult();
        if (returned == null || node.getTree() == null) {
            return;
        }
        Origin origin = result.getValue(returned);
        Dependence dependence = origin == null ? Dependence.NONE : origin.dependence;
        if (dependence.onCache) {
            if (reported.add(node.getTree())) {
                owner.fieldError(
                        node.getTree(),
                        "derived.field.result.unchecked",
                        dependence.fields.iterator().next());
            }
            return;
        }
        FlowStore before = result.getStoreBefore(node);
        if (before == null || before.tested.isEmpty()) {
            return;
        }
        for (VariableElement field : before.tested) {
            // The result for a tested field is its derived value, or, where the method found the
            // sentinel, whatever the method computes from abstract state instead.
            if (dependence.valueOf.contains(field)
                    || dependence.sentinelOf.contains(field)
                    || before.missed.contains(field)) {
                return;
            }
        }
        if (reported.add(node.getTree())) {
            owner.fieldError(
                    node.getTree(), "derived.field.result.branch", before.tested.iterator().next());
        }
    }

    /**
     * Reports a read of a derived field that may follow another read of it.
     *
     * @param node a field access
     * @param result the result of the analysis
     */
    private void checkRead(FieldAccessNode node, AnalysisResult<Origin, FlowStore> result) {
        VariableElement field = countedRead(node);
        if (field == null) {
            return;
        }
        FlowStore before = result.getStoreBefore(node);
        if (before != null
                && before.mayHaveRead.contains(field)
                && !(allowReadAfterOwnWrite && before.mustHaveWritten.contains(field))
                && reported.add(node.getTree())) {
            owner.fieldError(node.getTree(), "derived.field.multiple.reads", field);
        }
    }

    /**
     * Reports a write to a derived field that stores a value that is not derived, or that goes
     * through another object.
     *
     * @param node an assignment
     * @param result the result of the analysis
     */
    private void checkWrite(AssignmentNode node, AnalysisResult<Origin, FlowStore> result) {
        if (!(node.getTarget() instanceof FieldAccessNode)) {
            return;
        }
        FieldAccessNode target = (FieldAccessNode) node.getTarget();
        VariableElement field = target.getElement();
        // An update such as f++ is compiled to an assignment that has no position in the source.
        Tree where = node.getInSource() ? node.getTree() : target.getTree();
        if (!derivedFields.contains(field) || !reported.add(where)) {
            return;
        }
        Node expression = withoutConversions(node.getExpression());
        Origin origin = result.getValue(node.getExpression());
        if (!(target.getReceiver() instanceof ThisNode)) {
            // A method may initialize the cache of an object that it has just created, as a
            // constructor could, with a value computed from abstract state. The value read from
            // this object's own cache is not such a value for the other object.
            Origin receiver = result.getValue(target.getReceiver());
            boolean initializesNewObject =
                    receiver != null
                            && receiver.fresh
                            && origin != null
                            && !origin.tainted
                            && origin.reads.isEmpty()
                            && owner.derivationOf(field).isEmpty()
                            && !(expression instanceof NullLiteralNode);
            if (!initializesNewObject) {
                owner.fieldError(where, "derived.field.write.other", field);
                return;
            }
            for (MethodInvocationTree call : origin.impureCalls) {
                owner.impureCallError(call, field);
            }
            return;
        }
        if (expression instanceof NullLiteralNode
                || origin == null
                || !origin.isDerivedFor(field, owner.derivationOf(field))) {
            // Storing null resets the cache, which only a class with mutable abstract state
            // needs.
            owner.fieldError(where, "derived.field.write.underived", field);
            return;
        }
        for (MethodInvocationTree call : origin.impureCalls) {
            owner.impureCallError(call, field);
        }
    }

    /**
     * Returns the derived field that a node reads, if the read counts toward the single-read rule:
     * the field belongs to {@code this}, the node is not the target of an assignment, the field is
     * not volatile, and the read is not under a lock.
     *
     * @param node a field access
     * @return the derived field read, or null
     */
    private @Nullable VariableElement countedRead(FieldAccessNode node) {
        VariableElement field = node.getElement();
        if (node.isLValue()
                || !derivedFields.contains(field)
                || !(node.getReceiver() instanceof ThisNode)
                || field.getModifiers().contains(Modifier.VOLATILE)
                || synchronizedAccesses.contains(node.getTree())) {
            return null;
        }
        return field;
    }

    /**
     * Strips the conversions that leave a value unchanged.
     *
     * @param node an expression
     * @return the expression under its casts and conversions
     */
    private static Node withoutConversions(Node node) {
        while (true) {
            if (node instanceof TypeCastNode) {
                node = ((TypeCastNode) node).getOperand();
            } else if (node instanceof NarrowingConversionNode) {
                node = ((NarrowingConversionNode) node).getOperand();
            } else if (node instanceof WideningConversionNode) {
                node = ((WideningConversionNode) node).getOperand();
            } else {
                return node;
            }
        }
    }

    /**
     * How a value relates to the state of the derived fields, for the rule about results.
     *
     * <p>A derived field holds its initial value, the <em>sentinel</em>, until some thread stores
     * the derived value. A value read from the field therefore depends on whether that has
     * happened, until it is compared with the sentinel: on the branch where it differs, it is the
     * derived value.
     */
    static final class Dependence {

        /** The dependence of a value that has nothing to do with a derived field. */
        static final Dependence NONE =
                new Dependence(
                        false,
                        Collections.emptySet(),
                        null,
                        0,
                        Collections.emptySet(),
                        Collections.emptySet());

        /** Whether the value may differ depending on whether a derived field is computed. */
        final boolean onCache;

        /** The derived fields whose state the value may depend on. */
        final Set<VariableElement> fields;

        /** The derived field of which the value is exactly the value read plus a constant. */
        final @Nullable VariableElement rawField;

        /** The constant added to the value read from {@link #rawField}. */
        final long shift;

        /** The derived fields whose derived value determines this value on every path. */
        final Set<VariableElement> valueOf;

        /** The derived fields whose sentinel this value is known to be. */
        final Set<VariableElement> sentinelOf;

        /**
         * The constant this value is known to equal, as a {@code Long} or the null sentinel, or
         * null if it is not known to equal a constant.
         */
        @Nullable Object constant;

        /**
         * Creates a dependence.
         *
         * @param onCache whether the value may differ depending on the state of a derived field
         * @param fields the derived fields whose state the value may depend on
         * @param rawField the field of which the value is the value read plus a constant, or null
         * @param shift the constant added to the value read
         * @param valueOf the fields whose derived value determines this value
         * @param sentinelOf the fields whose sentinel this value is
         */
        Dependence(
                boolean onCache,
                Set<VariableElement> fields,
                @Nullable VariableElement rawField,
                long shift,
                Set<VariableElement> valueOf,
                Set<VariableElement> sentinelOf) {
            this.onCache = onCache;
            this.fields = fields;
            this.rawField = rawField;
            this.shift = shift;
            this.valueOf = valueOf;
            this.sentinelOf = sentinelOf;
        }

        /**
         * Returns the dependence of the value read from a derived field, plus a constant.
         *
         * @param field the field
         * @param shift the constant
         * @return the dependence of an unchecked read
         */
        static Dependence raw(VariableElement field, long shift) {
            return new Dependence(
                    true,
                    Collections.singleton(field),
                    field,
                    shift,
                    Collections.emptySet(),
                    Collections.emptySet());
        }

        /**
         * Returns the dependence of a value determined by the derived value of a field.
         *
         * @param field the field
         * @return the dependence of the derived value
         */
        static Dependence valueOf(VariableElement field) {
            return new Dependence(
                    false,
                    Collections.emptySet(),
                    null,
                    0,
                    Collections.singleton(field),
                    Collections.emptySet());
        }

        /**
         * Returns the dependence of a value known to be the sentinel of a field.
         *
         * @param field the field
         * @return the dependence of the sentinel
         */
        static Dependence sentinelOf(VariableElement field) {
            return new Dependence(
                    false,
                    Collections.emptySet(),
                    null,
                    0,
                    Collections.emptySet(),
                    Collections.singleton(field));
        }

        /**
         * Returns the dependence of a value that is now known to be the derived value of the fields
         * whose sentinel it was.
         *
         * @return the dependence with the sentinel fields moved to the derived-value fields
         */
        Dependence promoteSentinels() {
            Set<VariableElement> all = new LinkedHashSet<>(valueOf);
            all.addAll(sentinelOf);
            return new Dependence(onCache, fields, rawField, shift, all, Collections.emptySet());
        }

        /**
         * Returns this dependence for a value that is also known to equal a constant.
         *
         * @param value the constant, as a {@code Long} or the null sentinel
         * @return the dependence with the constant recorded
         */
        Dependence withConstant(@Nullable Object value) {
            Dependence result =
                    new Dependence(onCache, fields, rawField, shift, valueOf, sentinelOf);
            result.constant = value;
            return result;
        }

        /**
         * Returns this dependence for a value that is also the derived value of a field.
         *
         * @param field the field
         * @return the dependence with the field added to the derived-value fields
         */
        Dependence alsoValueOf(VariableElement field) {
            Set<VariableElement> all = new LinkedHashSet<>(valueOf);
            all.add(field);
            Dependence result = new Dependence(onCache, fields, rawField, shift, all, sentinelOf);
            result.constant = constant;
            return result;
        }

        /**
         * Returns the dependence of a computation that uses this value and another as operands.
         *
         * @param other the dependence of another operand
         * @return the dependence of the result
         */
        Dependence combine(Dependence other) {
            boolean dependent = onCache || other.onCache;
            Set<VariableElement> allFields = new LinkedHashSet<>(fields);
            allFields.addAll(other.fields);
            Set<VariableElement> allValues = new LinkedHashSet<>();
            if (!dependent) {
                allValues.addAll(valueOf);
                allValues.addAll(other.valueOf);
            }
            if (!dependent && allFields.isEmpty() && allValues.isEmpty()) {
                return NONE;
            }
            return new Dependence(dependent, allFields, null, 0, allValues, Collections.emptySet());
        }

        /**
         * Returns the dependence of a value that is this one on some paths and another on others.
         *
         * @param other the dependence on the other paths
         * @return the dependence that holds on all paths
         */
        Dependence join(Dependence other) {
            if (this.equals(other)) {
                return this;
            }
            Set<VariableElement> allFields = new LinkedHashSet<>(fields);
            allFields.addAll(other.fields);
            Set<VariableElement> commonSentinels = new LinkedHashSet<>(sentinelOf);
            commonSentinels.retainAll(other.sentinelOf);
            // A value that is the derived value of a field on some paths and its sentinel on
            // paths where the method decided not to compute it is the result for that field.
            Set<VariableElement> commonValues = new LinkedHashSet<>(valueOf);
            commonValues.addAll(sentinelOf);
            Set<VariableElement> otherValues = new LinkedHashSet<>(other.valueOf);
            otherValues.addAll(other.sentinelOf);
            commonValues.retainAll(otherValues);
            commonValues.removeAll(commonSentinels);
            boolean sameRaw =
                    rawField != null && rawField.equals(other.rawField) && shift == other.shift;
            Dependence result =
                    new Dependence(
                            onCache || other.onCache,
                            allFields,
                            sameRaw ? rawField : null,
                            sameRaw ? shift : 0,
                            commonValues,
                            commonSentinels);
            result.constant = Objects.equals(constant, other.constant) ? constant : null;
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Dependence)) {
                return false;
            }
            Dependence other = (Dependence) obj;
            return onCache == other.onCache
                    && fields.equals(other.fields)
                    && Objects.equals(rawField, other.rawField)
                    && shift == other.shift
                    && valueOf.equals(other.valueOf)
                    && sentinelOf.equals(other.sentinelOf)
                    && Objects.equals(constant, other.constant);
        }

        @Override
        public int hashCode() {
            return Objects.hash(onCache, fields, rawField, shift, valueOf, sentinelOf, constant);
        }
    }

    /** Where a value comes from. Values are ordered by inclusion of their sources. */
    static final class Origin implements AbstractValue<Origin> {

        /** A value computed from abstract state only. */
        static final Origin CLEAN =
                new Origin(false, true, Collections.emptySet(), Collections.emptySet());

        /** A value that is not derived. */
        static final Origin TAINTED =
                new Origin(true, false, Collections.emptySet(), Collections.emptySet());

        /** Whether the value may depend on something that is not abstract state. */
        final boolean tainted;

        /** Whether the value may be a computation other than a direct derivation call. */
        final boolean computed;

        /** The derived fields whose value, as read, this value may be. */
        final Set<VariableElement> reads;

        /** The derivation methods whose result this value may be. */
        final Set<String> derivations;

        /** The calls in the computation that are not known to be side-effect free. */
        Set<MethodInvocationTree> impureCalls = Collections.emptySet();

        /** Whether the value is certainly an object that this method created with {@code new}. */
        boolean fresh = false;

        /** How the value relates to the state of the derived fields. */
        Dependence dependence = Dependence.NONE;

        /**
         * Creates a value.
         *
         * @param tainted whether the value may depend on something that is not abstract state
         * @param computed whether the value may be a computation other than a derivation call
         * @param reads the derived fields whose value, as read, this value may be
         * @param derivations the derivation methods whose result this value may be
         */
        Origin(
                boolean tainted,
                boolean computed,
                Set<VariableElement> reads,
                Set<String> derivations) {
            this.tainted = tainted;
            this.computed = computed;
            this.reads = reads;
            this.derivations = derivations;
        }

        /**
         * Returns the value of a read of a derived field.
         *
         * @param field the field
         * @return the value read from the field
         */
        static Origin readOf(VariableElement field) {
            return new Origin(false, false, Collections.singleton(field), Collections.emptySet());
        }

        /**
         * Returns the value of a call of a derivation method on this.
         *
         * @param name the name of the method
         * @return the result of the derivation
         */
        static Origin derivation(String name) {
            return new Origin(false, false, Collections.emptySet(), Collections.singleton(name));
        }

        /**
         * Returns this value with additional calls that are not known to be side-effect free.
         *
         * @param calls the calls
         * @return the value with the calls recorded
         */
        Origin withImpureCalls(Set<MethodInvocationTree> calls) {
            if (calls.isEmpty() || impureCalls.containsAll(calls)) {
                return this;
            }
            Origin result = new Origin(tainted, computed, reads, derivations);
            Set<MethodInvocationTree> all = new LinkedHashSet<>(impureCalls);
            all.addAll(calls);
            result.impureCalls = all;
            result.fresh = fresh;
            result.dependence = dependence;
            return result;
        }

        /**
         * Returns this value with a different relation to the state of the derived fields.
         *
         * @param newDependence the new relation
         * @return the value with that relation
         */
        Origin withDependence(Dependence newDependence) {
            if (dependence.equals(newDependence)) {
                return this;
            }
            Origin result = new Origin(tainted, computed, reads, derivations);
            result.impureCalls = impureCalls;
            result.fresh = fresh;
            result.dependence = newDependence;
            return result;
        }

        /**
         * Returns this value marked as an object that the method created.
         *
         * @return the value of an object creation
         */
        Origin asFresh() {
            Origin result = new Origin(tainted, computed, reads, derivations);
            result.impureCalls = impureCalls;
            result.fresh = true;
            result.dependence = dependence;
            return result;
        }

        /**
         * Returns the value of a computation that uses this value as an operand.
         *
         * @return a tainted value if this value is tainted or may be a cache read, and a computed
         *     value otherwise
         */
        Origin usedInComputation() {
            if (tainted || !reads.isEmpty()) {
                return TAINTED;
            }
            return CLEAN.withImpureCalls(impureCalls);
        }

        /**
         * Returns true if the value may be stored in a derived field.
         *
         * @param field the derived field
         * @param derivation the name of the derivation method the field requires, or the empty
         *     string
         * @return true if the value is the value read from the field or a derived value
         */
        boolean isDerivedFor(VariableElement field, String derivation) {
            if (tainted) {
                return false;
            }
            for (VariableElement read : reads) {
                if (!read.equals(field)) {
                    return false;
                }
            }
            if (derivation.isEmpty()) {
                return true;
            }
            if (computed) {
                return false;
            }
            for (String name : derivations) {
                if (!name.equals(derivation)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Origin leastUpperBound(Origin other) {
            if (this.equals(other)) {
                return this;
            }
            Set<VariableElement> allReads = new LinkedHashSet<>(reads);
            allReads.addAll(other.reads);
            Set<String> allDerivations = new LinkedHashSet<>(derivations);
            allDerivations.addAll(other.derivations);
            Origin result =
                    new Origin(
                            tainted || other.tainted,
                            computed || other.computed,
                            allReads,
                            allDerivations);
            Set<MethodInvocationTree> all = new LinkedHashSet<>(impureCalls);
            all.addAll(other.impureCalls);
            result.impureCalls = all;
            result.fresh = fresh && other.fresh;
            result.dependence = dependence.join(other.dependence);
            return result;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Origin)) {
                return false;
            }
            Origin other = (Origin) obj;
            return tainted == other.tainted
                    && computed == other.computed
                    && reads.equals(other.reads)
                    && derivations.equals(other.derivations)
                    && impureCalls.equals(other.impureCalls)
                    && fresh == other.fresh
                    && dependence.equals(other.dependence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    tainted, computed, reads, derivations, impureCalls, fresh, dependence);
        }
    }

    /** The facts that hold at a program point. */
    static final class FlowStore implements Store<FlowStore> {

        /** Where the value of each local variable comes from. */
        final Map<Element, Origin> locals = new HashMap<>();

        /** The derived fields that may already have been read. */
        final Set<VariableElement> mayHaveRead = new HashSet<>();

        /** The derived fields that have certainly been written. */
        final Set<VariableElement> mustHaveWritten = new HashSet<>();

        /** The derived fields whose state may have decided which path was taken. */
        final Set<VariableElement> tested = new HashSet<>();

        /** The derived fields that are known to hold their sentinel on every path to here. */
        final Set<VariableElement> missed = new HashSet<>();

        @Override
        public FlowStore copy() {
            FlowStore result = new FlowStore();
            result.locals.putAll(locals);
            result.mayHaveRead.addAll(mayHaveRead);
            result.mustHaveWritten.addAll(mustHaveWritten);
            result.tested.addAll(tested);
            result.missed.addAll(missed);
            return result;
        }

        @Override
        public FlowStore leastUpperBound(FlowStore other) {
            FlowStore result = copy();
            for (Map.Entry<Element, Origin> entry : other.locals.entrySet()) {
                result.locals.merge(entry.getKey(), entry.getValue(), Origin::leastUpperBound);
            }
            result.mayHaveRead.addAll(other.mayHaveRead);
            result.mustHaveWritten.retainAll(other.mustHaveWritten);
            result.tested.addAll(other.tested);
            result.missed.retainAll(other.missed);
            return result;
        }

        @Override
        public FlowStore widenedUpperBound(FlowStore previous) {
            return leastUpperBound(previous);
        }

        @Override
        public boolean canAlias(JavaExpression a, JavaExpression b) {
            return true;
        }

        @Override
        public String visualize(CFGVisualizer<?, FlowStore, ?> viz) {
            return locals
                    + " read "
                    + mayHaveRead
                    + " written "
                    + mustHaveWritten
                    + " tested "
                    + tested;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof FlowStore)) {
                return false;
            }
            FlowStore other = (FlowStore) obj;
            return locals.equals(other.locals)
                    && mayHaveRead.equals(other.mayHaveRead)
                    && mustHaveWritten.equals(other.mustHaveWritten)
                    && tested.equals(other.tested)
                    && missed.equals(other.missed);
        }

        @Override
        public int hashCode() {
            return Objects.hash(locals, mayHaveRead, mustHaveWritten, tested, missed);
        }
    }

    /** The transfer function. */
    final class Transfer
            extends AbstractNodeVisitor<
                    TransferResult<Origin, FlowStore>, TransferInput<Origin, FlowStore>>
            implements ForwardTransferFunction<Origin, FlowStore> {

        @Override
        public FlowStore initialStore(
                UnderlyingAST underlyingAST, List<LocalVariableNode> parameters) {
            FlowStore store = new FlowStore();
            for (LocalVariableNode parameter : parameters) {
                // A value passed by the caller is not derived from the abstract state.
                store.locals.put(parameter.getElement(), Origin.TAINTED);
            }
            return store;
        }

        /**
         * Returns the value of an operand.
         *
         * @param node the operand
         * @param in the input of the node that uses the operand
         * @return the value of the operand, or a clean value if it has none
         */
        private Origin valueOf(@Nullable Node node, TransferInput<Origin, FlowStore> in) {
            if (node == null) {
                return Origin.CLEAN;
            }
            Origin value = in.getValueOfSubNode(node);
            return value == null ? Origin.CLEAN : value;
        }

        /**
         * Returns the value of a computation over operands.
         *
         * @param operands the operands
         * @param in the input of the node
         * @return a tainted value if an operand is tainted or may be a cache read, and a computed
         *     value otherwise
         */
        private Origin computation(
                Iterable<? extends Node> operands, TransferInput<Origin, FlowStore> in) {
            Origin result = Origin.CLEAN;
            Dependence dependence = Dependence.NONE;
            boolean tainted = false;
            for (Node operand : operands) {
                Origin operandOrigin = valueOf(operand, in);
                dependence = dependence.combine(operandOrigin.dependence);
                Origin used = operandOrigin.usedInComputation();
                if (used.tainted) {
                    tainted = true;
                } else {
                    result = result.withImpureCalls(used.impureCalls);
                }
            }
            return (tainted ? Origin.TAINTED : result).withDependence(dependence);
        }

        /**
         * Records that the state of the derived fields a value depends on may decide which path is
         * taken, if the value is a condition.
         *
         * @param node the node that produced the value
         * @param value the value
         * @param store the store to update
         */
        private void recordTest(Node node, Origin value, FlowStore store) {
            if (value.dependence.onCache && node.getType().getKind() == TypeKind.BOOLEAN) {
                store.tested.addAll(value.dependence.fields);
            }
        }

        /**
         * Returns the value of an integral literal, possibly negated.
         *
         * @param node an expression
         * @return the value of the literal, or null if the expression is not an integral literal
         */
        private @Nullable Long integralLiteral(Node node) {
            node = withoutConversions(node);
            long sign = 1;
            if (node instanceof NumericalMinusNode) {
                node = withoutConversions(((NumericalMinusNode) node).getOperand());
                sign = -1;
            }
            if (node instanceof IntegerLiteralNode) {
                return sign * ((IntegerLiteralNode) node).getValue();
            }
            if (node instanceof LongLiteralNode) {
                return sign * ((LongLiteralNode) node).getValue();
            }
            if (node instanceof FieldAccessNode) {
                // A named compile-time constant, such as a sentinel declared static final.
                Object constant = ((FieldAccessNode) node).getElement().getConstantValue();
                if (constant instanceof Integer || constant instanceof Long) {
                    return sign * ((Number) constant).longValue();
                }
            }
            return null;
        }

        /**
         * Returns the constant a literal denotes, in the form used for sentinels.
         *
         * @param literal a literal
         * @return a {@code Long}, the null sentinel, or null if the literal is of another kind
         */
        private @Nullable Object constantOf(Node literal) {
            if (withoutConversions(literal) instanceof NullLiteralNode) {
                return MutabilityDerivedFieldChecker.NULL_SENTINEL;
            }
            return integralLiteral(literal);
        }

        /**
         * Returns true if comparing a value with a literal tests whether a derived field still
         * holds its sentinel.
         *
         * @param dependence the dependence of the value, which is the field's value plus a constant
         * @param literal the literal the value is compared with
         * @return true if the value equals the literal exactly when the field holds its sentinel
         */
        private boolean isSentinelTest(Dependence dependence, Node literal) {
            if (dependence.rawField == null) {
                return false;
            }
            Object sentinel = owner.sentinelOf(dependence.rawField);
            Node stripped = withoutConversions(literal);
            if (stripped instanceof NullLiteralNode) {
                return sentinel == MutabilityDerivedFieldChecker.NULL_SENTINEL
                        && dependence.shift == 0;
            }
            Long value = integralLiteral(literal);
            return value != null
                    && sentinel instanceof Long
                    && value - dependence.shift == (Long) sentinel;
        }

        /**
         * Handles the addition or subtraction of a constant, which keeps the exact relation to the
         * value read from a derived field, as in an offset encoding.
         *
         * @param node the operation
         * @param left the left operand
         * @param right the right operand
         * @param sign 1 for an addition and -1 for a subtraction
         * @param in the input of the node
         * @return the result of the operation
         */
        private TransferResult<Origin, FlowStore> shift(
                Node node, Node left, Node right, int sign, TransferInput<Origin, FlowStore> in) {
            Origin value = computation(node.getOperands(), in);
            Dependence operand = valueOf(left, in).dependence;
            Long constant = integralLiteral(right);
            if (operand.rawField != null && constant != null) {
                value =
                        value.withDependence(
                                Dependence.raw(operand.rawField, operand.shift + sign * constant));
            }
            return new RegularTransferResult<>(value, in.getRegularStore());
        }

        @Override
        public TransferResult<Origin, FlowStore> visitNumericalAddition(
                NumericalAdditionNode node, TransferInput<Origin, FlowStore> in) {
            return shift(node, node.getLeftOperand(), node.getRightOperand(), 1, in);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitNumericalSubtraction(
                NumericalSubtractionNode node, TransferInput<Origin, FlowStore> in) {
            return shift(node, node.getLeftOperand(), node.getRightOperand(), -1, in);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitNode(
                Node node, TransferInput<Origin, FlowStore> in) {
            Origin value = computation(node.getOperands(), in);
            if (in.containsTwoStores()
                    && (node instanceof ConditionalAndNode || node instanceof ConditionalOrNode)) {
                // The operands of a short-circuit operator have already split the store; keep
                // what is known on each branch.
                FlowStore thenStore = in.getThenStore();
                FlowStore elseStore = in.getElseStore();
                recordTest(node, value, thenStore);
                recordTest(node, value, elseStore);
                return new ConditionalTransferResult<>(value, thenStore, elseStore);
            }
            FlowStore store = in.getRegularStore();
            recordTest(node, value, store);
            return new RegularTransferResult<>(value, store);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitLocalVariable(
                LocalVariableNode node, TransferInput<Origin, FlowStore> in) {
            FlowStore store = in.getRegularStore();
            Origin value = store.locals.get(node.getElement());
            return new RegularTransferResult<>(value == null ? Origin.TAINTED : value, store);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitFieldAccess(
                FieldAccessNode node, TransferInput<Origin, FlowStore> in) {
            FlowStore store = in.getRegularStore();
            VariableElement field = node.getElement();
            if (node.isLValue()) {
                return new RegularTransferResult<>(Origin.CLEAN, store);
            }
            if (derivedFields.contains(field) && node.getReceiver() instanceof ThisNode) {
                if (countedRead(node) == null) {
                    // A volatile field or a read under a lock: ordered with the writes, and not
                    // subject to the rules for racy reads.
                    return new RegularTransferResult<>(Origin.readOf(field), store);
                }
                store.mayHaveRead.add(field);
                Origin value = Origin.readOf(field).withDependence(Dependence.raw(field, 0));
                if (field.asType().getKind() != TypeKind.BOOLEAN) {
                    return new RegularTransferResult<>(value, store);
                }
                // A derived flag such as String.hashIsZero: where it is set, a value known to be
                // the sentinel of another derived field is that field's derived value.
                store.tested.add(field);
                FlowStore set = store.copy();
                for (Map.Entry<Element, Origin> entry : set.locals.entrySet()) {
                    Origin local = entry.getValue();
                    if (!local.dependence.sentinelOf.isEmpty()) {
                        entry.setValue(local.withDependence(local.dependence.promoteSentinels()));
                    }
                }
                return new ConditionalTransferResult<>(value, set, store);
            }
            if (derivedFields.contains(field) || owner.isAssignable(field)) {
                return new RegularTransferResult<>(Origin.TAINTED, store);
            }
            return new RegularTransferResult<>(
                    computation(Collections.singleton(node.getReceiver()), in), store);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitAssignment(
                AssignmentNode node, TransferInput<Origin, FlowStore> in) {
            FlowStore store = in.getRegularStore();
            Origin value = valueOf(node.getExpression(), in);
            Node target = node.getTarget();
            if (target instanceof LocalVariableNode) {
                if (!value.dependence.onCache) {
                    // On a path where a derived field is known to hold its sentinel, the method is
                    // computing the field's value: what it computes from abstract state there is
                    // the result for that field.
                    Dependence dependence = value.dependence;
                    for (VariableElement field : store.missed) {
                        dependence = dependence.alsoValueOf(field);
                    }
                    value = value.withDependence(dependence);
                }
                store.locals.put(((LocalVariableNode) target).getElement(), value);
            } else if (target instanceof FieldAccessNode) {
                FieldAccessNode access = (FieldAccessNode) target;
                if (derivedFields.contains(access.getElement())
                        && access.getReceiver() instanceof ThisNode) {
                    VariableElement field = access.getElement();
                    store.mustHaveWritten.add(field);
                    // The value stored is the derived value of the field, and so is the local
                    // variable it was taken from, directly or by adding a constant.
                    Dependence derived = Dependence.valueOf(field);
                    value = value.withDependence(derived);
                    Element source = writtenLocal(node.getExpression());
                    if (source != null) {
                        Origin old = store.locals.get(source);
                        store.locals.put(
                                source,
                                (old == null ? Origin.TAINTED : old).withDependence(derived));
                    }
                    if (field.asType().getKind() == TypeKind.BOOLEAN) {
                        // Setting a derived flag such as String.hashIsZero records that the
                        // derived value of another field equals its sentinel: a local variable
                        // known to equal that sentinel is that field's derived value.
                        for (Map.Entry<Element, Origin> entry : store.locals.entrySet()) {
                            Dependence local = entry.getValue().dependence;
                            if (local.constant == null) {
                                continue;
                            }
                            for (VariableElement other : derivedFields) {
                                if (other.asType().getKind() != TypeKind.BOOLEAN
                                        && local.constant.equals(owner.sentinelOf(other))) {
                                    local = local.alsoValueOf(other);
                                }
                            }
                            entry.setValue(entry.getValue().withDependence(local));
                        }
                    }
                }
            }
            return new RegularTransferResult<>(value, store);
        }

        /**
         * Returns the local variable from which the value written to a derived field is taken: the
         * variable itself, the target of an assignment, or a variable to which a constant is added.
         *
         * @param expression the expression written to the field
         * @return the local variable, or null
         */
        private @Nullable Element writtenLocal(Node expression) {
            Element local = localOf(expression);
            if (local != null) {
                return local;
            }
            Node stripped = withoutConversions(expression);
            if (stripped instanceof NumericalAdditionNode) {
                NumericalAdditionNode sum = (NumericalAdditionNode) stripped;
                if (integralLiteral(sum.getRightOperand()) != null) {
                    return localOf(sum.getLeftOperand());
                }
            } else if (stripped instanceof NumericalSubtractionNode) {
                NumericalSubtractionNode difference = (NumericalSubtractionNode) stripped;
                if (integralLiteral(difference.getRightOperand()) != null) {
                    return localOf(difference.getLeftOperand());
                }
            }
            return null;
        }

        @Override
        public TransferResult<Origin, FlowStore> visitTypeCast(
                TypeCastNode node, TransferInput<Origin, FlowStore> in) {
            return new RegularTransferResult<>(
                    valueOf(node.getOperand(), in), in.getRegularStore());
        }

        @Override
        public TransferResult<Origin, FlowStore> visitNarrowingConversion(
                NarrowingConversionNode node, TransferInput<Origin, FlowStore> in) {
            return new RegularTransferResult<>(
                    valueOf(node.getOperand(), in), in.getRegularStore());
        }

        @Override
        public TransferResult<Origin, FlowStore> visitWideningConversion(
                WideningConversionNode node, TransferInput<Origin, FlowStore> in) {
            return new RegularTransferResult<>(
                    valueOf(node.getOperand(), in), in.getRegularStore());
        }

        @Override
        public TransferResult<Origin, FlowStore> visitStringConversion(
                StringConversionNode node, TransferInput<Origin, FlowStore> in) {
            return new RegularTransferResult<>(
                    computation(Collections.singleton(node.getOperand()), in),
                    in.getRegularStore());
        }

        @Override
        public TransferResult<Origin, FlowStore> visitTernaryExpression(
                TernaryExpressionNode node, TransferInput<Origin, FlowStore> in) {
            FlowStore store = in.getRegularStore();
            Origin value = store.locals.get(node.getTernaryExpressionVar().getElement());
            if (value == null) {
                value =
                        valueOf(node.getThenOperand(), in)
                                .leastUpperBound(valueOf(node.getElseOperand(), in));
            }
            return new RegularTransferResult<>(value, store);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitMethodInvocation(
                MethodInvocationNode node, TransferInput<Origin, FlowStore> in) {
            FlowStore store = in.getRegularStore();
            ExecutableElement callee = node.getTarget().getMethod();
            Node receiver = node.getTarget().getReceiver();
            if (!node.getInSource()) {
                // Boxing and unboxing are compiled to calls that leave the value unchanged.
                Node operand = node.getArguments().isEmpty() ? receiver : node.getArgument(0);
                return new RegularTransferResult<>(valueOf(operand, in), store);
            }
            Set<Node> operands = new LinkedHashSet<>(node.getArguments());
            boolean onThis = receiver instanceof ThisNode || ElementUtils.isStatic(callee);
            if (!onThis) {
                operands.add(receiver);
            }
            Origin value = computation(operands, in);
            if (value.tainted) {
                return new RegularTransferResult<>(value, store);
            }
            if (receiver instanceof ThisNode && owner.isDerivation(callee)) {
                return new RegularTransferResult<>(
                        Origin.derivation(callee.getSimpleName().toString())
                                .withImpureCalls(value.impureCalls)
                                .withDependence(value.dependence),
                        store);
            }
            MethodInvocationTree tree = node.getTree();
            MutabilityDerivedFieldChecker.InlinedCallee inlined =
                    onThis ? owner.inlineCallee(callee) : null;
            if (inlined != null) {
                return new RegularTransferResult<>(
                        inlined.clean
                                ? value.withImpureCalls(inlined.impureCalls)
                                : Origin.TAINTED.withDependence(value.dependence),
                        store);
            }
            if (!owner.isSideEffectFree(callee) && tree != null) {
                value = value.withImpureCalls(Collections.singleton(tree));
            }
            return new RegularTransferResult<>(value, store);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitObjectCreation(
                ObjectCreationNode node, TransferInput<Origin, FlowStore> in) {
            return new RegularTransferResult<>(
                    computation(node.getArguments(), in).asFresh(), in.getRegularStore());
        }

        @Override
        public TransferResult<Origin, FlowStore> visitEqualTo(
                EqualToNode node, TransferInput<Origin, FlowStore> in) {
            return compare(node, node.getLeftOperand(), node.getRightOperand(), true, in);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitNotEqual(
                NotEqualNode node, TransferInput<Origin, FlowStore> in) {
            return compare(node, node.getLeftOperand(), node.getRightOperand(), false, in);
        }

        /**
         * Handles a comparison. On the branch where a local variable equals a literal, the variable
         * holds that constant, which does not depend on where its value came from.
         *
         * @param node the comparison
         * @param left the left operand
         * @param right the right operand
         * @param equalOnThen whether the operands are equal on the then branch
         * @param in the input of the node
         * @return the result, with the local variable refined on the branch where it is equal
         */
        private TransferResult<Origin, FlowStore> compare(
                Node node,
                Node left,
                Node right,
                boolean equalOnThen,
                TransferInput<Origin, FlowStore> in) {
            Origin value = computation(node.getOperands(), in);
            Element local = null;
            Node literal = null;
            if (isLiteral(right)) {
                local = localOf(left);
                literal = right;
            } else if (isLiteral(left)) {
                local = localOf(right);
                literal = left;
            }
            FlowStore store = in.getRegularStore();
            recordTest(node, value, store);
            if (local == null) {
                return new RegularTransferResult<>(value, store);
            }
            Origin tested = store.locals.get(local);
            Dependence dependence = tested == null ? Dependence.NONE : tested.dependence;
            FlowStore equal = store.copy();
            FlowStore different = store.copy();
            if (isSentinelTest(dependence, literal)) {
                // The field holds its sentinel on one branch and its derived value on the other.
                VariableElement field = dependence.rawField;
                equal.missed.add(field);
                equal.locals.put(
                        local,
                        Origin.CLEAN.withDependence(
                                Dependence.sentinelOf(field).withConstant(constantOf(literal))));
                different.locals.put(
                        local,
                        (dependence.shift == 0 ? Origin.readOf(field) : Origin.CLEAN)
                                .withDependence(Dependence.valueOf(field)));
            } else {
                equal.locals.put(
                        local,
                        Origin.CLEAN.withDependence(
                                new Dependence(
                                                false,
                                                Collections.emptySet(),
                                                null,
                                                0,
                                                dependence.valueOf,
                                                dependence.sentinelOf)
                                        .withConstant(constantOf(literal))));
            }
            return equalOnThen
                    ? new ConditionalTransferResult<>(value, equal, different)
                    : new ConditionalTransferResult<>(value, different, equal);
        }

        /**
         * Returns the local variable whose value an expression is: the variable itself, or the
         * target of an assignment.
         *
         * @param node an expression
         * @return the local variable, or null
         */
        private @Nullable Element localOf(Node node) {
            node = withoutConversions(node);
            if (node instanceof AssignmentNode) {
                node = ((AssignmentNode) node).getTarget();
            }
            return node instanceof LocalVariableNode
                    ? ((LocalVariableNode) node).getElement()
                    : null;
        }

        /**
         * Returns true if an expression is a literal, possibly negated.
         *
         * @param node an expression
         * @return true if the expression is a literal
         */
        private boolean isLiteral(Node node) {
            node = withoutConversions(node);
            if (node instanceof NumericalMinusNode) {
                node = withoutConversions(((NumericalMinusNode) node).getOperand());
            }
            return node instanceof ValueLiteralNode
                    || (node instanceof FieldAccessNode
                            && ((FieldAccessNode) node).getElement().getConstantValue() != null);
        }

        @Override
        public TransferResult<Origin, FlowStore> visitConditionalNot(
                ConditionalNotNode node, TransferInput<Origin, FlowStore> in) {
            Origin value = computation(node.getOperands(), in);
            if (in.containsTwoStores()) {
                FlowStore thenStore = in.getElseStore();
                FlowStore elseStore = in.getThenStore();
                recordTest(node, value, thenStore);
                recordTest(node, value, elseStore);
                return new ConditionalTransferResult<>(value, thenStore, elseStore);
            }
            FlowStore store = in.getRegularStore();
            recordTest(node, value, store);
            return new RegularTransferResult<>(value, store);
        }
    }
}
