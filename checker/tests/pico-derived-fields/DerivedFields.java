import org.checkerframework.checker.mutability.qual.Assignable;
import org.checkerframework.checker.mutability.qual.Derivation;
import org.checkerframework.checker.mutability.qual.Derived;
import org.checkerframework.checker.mutability.qual.Immutable;

// A lazily initialized hash code, as in java.lang.String.
@Immutable class DerivedHash {
    final int a;
    final int b;
    private @Assignable @Derived(by = "computeHash") int hash;

    DerivedHash(int a, int b) {
        this.a = a;
        this.b = b;
    }

    @Derivation
    int computeHash() {
        return 31 * a + b;
    }

    // The local-copy idiom: one read, and the write stores a derived value.
    int goodHash() {
        int h = hash;
        if (h == 0) {
            h = computeHash();
            hash = h;
        }
        return h;
    }

    // Reading the field twice can return the default after seeing the hash.
    int doubleRead() {
        if (hash == 0) {
            hash = computeHash();
        }
        // :: error: (derived.field.multiple.reads)
        // :: error: (derived.field.result.unchecked)
        return hash;
    }

    void poison() {
        // :: error: (derived.field.write.underived)
        hash = 42;
    }

    void poisonThroughLocal() {
        int h = 42;
        // :: error: (derived.field.write.underived)
        hash = h;
    }

    void increment() {
        // :: error: (derived.field.write.underived)
        hash++;
    }

    // A local that is updated in place no longer holds a derived value.
    void adjusted() {
        int h = computeHash();
        h += 1;
        // :: error: (derived.field.write.underived)
        hash = h;
    }

    void writeOther(DerivedHash other) {
        // :: error: (derived.field.write.other)
        other.hash = other.computeHash();
    }
}

// A lazily initialized reference, as in Guava's cached views.
@Immutable class DerivedText {
    final int a;
    private @Assignable @Derived String text;

    DerivedText(int a) {
        this.a = a;
    }

    @Derivation
    String render() {
        return "<" + a + ">";
    }

    String goodText() {
        String r = text;
        return (r == null) ? text = render() : r;
    }

    String doubleRead() {
        // :: error: (derived.field.multiple.reads)
        // :: error: (derived.field.result.unchecked)
        return (text == null) ? text = render() : text;
    }
}

@Immutable class DerivedDeclarations {
    // :: error: (derived.field.not.assignable)
    private @Derived int notAssignable;

    // :: error: (derived.field.not.private)
    @Assignable @Derived int notPrivate;

    // :: error: (derived.field.wide)
    private @Assignable @Derived long wide;

    private volatile @Assignable @Derived long wideVolatile;

    DerivedDeclarations() {
        this.notAssignable = 0;
    }
}

@Immutable class DerivationRules {
    final int a;
    private @Assignable int scratch;

    DerivationRules(int a) {
        this.a = a;
    }

    @Derivation
    int readsAssignable() {
        // :: error: (derivation.reads.assignable)
        return a + scratch;
    }

    @Derivation
    int writes() {
        // :: error: (derivation.field.write)
        scratch = 1;
        return a;
    }

    int helper() {
        return a;
    }

    @Derivation
    int callsImpure() {
        // :: error: (derivation.call.impure)
        return helper();
    }

    @Derivation
    int base() {
        return a;
    }
}

@Immutable class DerivationOverride extends DerivationRules {
    DerivationOverride(int a) {
        super(a);
    }

    @Override
    // :: error: (derivation.override.missing)
    int base() {
        return 0;
    }
}

// A derived field that names no derivation method may store an inline derivation.
@Immutable class InlineDerivations {
    final int a;
    final int b;
    private @Assignable @Derived int hash;
    private @Assignable @Derived String text;
    private @Assignable int scratch;

    InlineDerivations(int a, int b) {
        this.a = a;
        this.b = b;
    }

    int goodHash() {
        int h = hash;
        if (h == 0) {
            int r = 17;
            r = 31 * r + a;
            r += b;
            hash = h = r;
        }
        return h;
    }

    // Inside the branch the local copy is known to be the default, so it may accumulate the
    // derived value, as java.lang.String.hashCode once did.
    int accumulatesInCopy() {
        int h = hash;
        if (h == 0) {
            h = 31 * h + a;
            hash = h;
        }
        return h;
    }

    // Outside such a branch the local copy may hold the cached value.
    int accumulatesWithoutTest() {
        int h = hash;
        h = 31 * h + a;
        // :: error: (derived.field.write.underived)
        hash = h;
        return h;
    }

    // An offset encoding, as in java.math.BigInteger: zero means "not yet computed".
    int offsetEncoded() {
        int n = hash - 1;
        if (n == -1) {
            n = a + b;
            hash = n + 1;
        }
        return n;
    }

    // One read on each path is one read.
    int readOnEitherPath(boolean first) {
        int h;
        if (first) {
            h = hash;
        } else {
            h = hash;
        }
        if (h == 0) {
            h = a + b;
            hash = h;
        }
        return h;
    }

    // The value read may be the initial value, so it is not a result.
    int returnsUnchecked() {
        // :: error: (derived.field.result.unchecked)
        return hash;
    }

    // Whether the field has been computed is not a property of the object.
    boolean isCached() {
        // :: error: (derived.field.result.unchecked)
        return hash != 0;
    }

    boolean isCachedByBranch() {
        int h = hash;
        if (h != 0) {
            // :: error: (derived.field.result.branch)
            return true;
        }
        return false;
    }

    // Where the method finds the initial value, what it returns defines the result. Returning
    // the initial value is right when the derived value equals it, as for an absent component in
    // java.net.URI; the check cannot tell that from a method that forgot to compute.
    int returnsInitialValueOnMiss() {
        int h = hash;
        if (h == 0) {
            return h;
        }
        return h;
    }

    // The derived value may be the initial value, decided by the abstract state.
    int computedOnlyWhenPositive() {
        int h = hash;
        if (h == 0 && a > 0) {
            hash = h = a + b;
        }
        return h;
    }

    // A value computed from the derived value is a result.
    int doubled() {
        int h = hash;
        if (h == 0) {
            h = a + b;
            hash = h;
        }
        return h * 2;
    }

    // A read in a loop is repeated.
    int readInLoop() {
        int sum = 0;
        for (int i = 0; i < 2; i++) {
            // :: error: (derived.field.multiple.reads)
            sum += hash;
        }
        // :: error: (derived.field.result.unchecked)
        return sum;
    }

    // A read may return an older value even after this method wrote the field.
    int readAfterOwnWrite() {
        int h = hash;
        if (h != 0) {
            return h;
        }
        hash = a + b;
        // :: error: (derived.field.multiple.reads)
        // :: error: (derived.field.result.unchecked)
        return hash;
    }

    String goodText() {
        String r = text;
        if (r == null) {
            text = r = new String("<" + a + ">");
        }
        return r;
    }

    void readsAssignable() {
        // :: error: (derived.field.write.underived)
        hash = a + scratch;
    }

    void readsAssignableThroughLocal() {
        int s = scratch;
        int h = a + s;
        // :: error: (derived.field.write.underived)
        hash = h;
    }

    // The value read from the field may be stored back, but not a value computed from it.
    void dependsOnRead() {
        int h = hash;
        int next = h + 1;
        // :: error: (derived.field.write.underived)
        hash = next;
    }

    void usesParameter(int p) {
        // :: error: (derived.field.write.underived)
        hash = a + p;
    }

    void reset() {
        // :: error: (derived.field.write.underived)
        text = null;
    }

    int helper() {
        return a;
    }

    void callsImpure() {
        // :: error: (derived.field.write.impure)
        hash = helper();
    }

    // A call on this to a method that cannot be overridden is checked as part of the write.
    private int sum() {
        return a + b;
    }

    private int sumWithScratch() {
        return a + scratch;
    }

    void callsPrivate() {
        hash = sum();
    }

    void callsPrivateReadingAssignable() {
        // :: error: (derived.field.write.underived)
        hash = sumWithScratch();
    }

    // Reads under a lock are ordered with the writes.
    int lockedDoubleRead() {
        synchronized (this) {
            if (hash == 0) {
                hash = a;
            }
            return hash;
        }
    }

    // Member classes can access the private fields, so they follow the same rules.
    class Inner {
        int doubleRead() {
            if (hash == 0) {
                hash = a;
            }
            // :: error: (derived.field.multiple.reads)
            // :: error: (derived.field.result.unchecked)
            return hash;
        }
    }

    static class Sibling {
        void seed(InlineDerivations other) {
            // :: error: (derived.field.write.other)
            other.hash = 1;
        }
    }
}

// A cache that is also initialized through a newly created object, as in Guava's reversed views.
@Immutable class Reversible {
    final int a;
    private @Assignable @Derived Reversible reverse;

    Reversible(int a) {
        this.a = a;
    }

    // The reverse of the reverse is this object, so the method that creates the reverse also
    // fills in its cache.
    Reversible reverse() {
        Reversible result = reverse;
        if (result == null) {
            result = reverse = new Reversible(-a);
            result.reverse = this;
        }
        return result;
    }

    static Reversible make(int a) {
        return new Reversible(a);
    }

    // The result of a call may be an object that is already shared.
    void seedsCallResult() {
        Reversible other = make(-a);
        // :: error: (derived.field.write.other)
        other.reverse = this;
    }

    void seedsParameter(Reversible other) {
        // :: error: (derived.field.write.other)
        other.reverse = this;
    }

    // The value in this object's cache is not a derived value for another object.
    void copiesOwnCache() {
        Reversible copy = new Reversible(a);
        // :: error: (derived.field.write.other)
        copy.reverse = reverse;
    }

    // A value that depends on a parameter is not derived.
    void seedsWithParameter(Reversible value) {
        Reversible fresh = new Reversible(a);
        // :: error: (derived.field.write.other)
        fresh.reverse = value;
    }
}

// A hash whose derived value may equal the initial value, with a flag, as in java.lang.String.
@Immutable class ZeroHash {
    final int a;
    private @Assignable @Derived int hash;
    private @Assignable @Derived boolean hashIsZero;

    ZeroHash(int a) {
        this.a = a;
    }

    int hash() {
        int h = hash;
        if (h == 0 && !hashIsZero) {
            h = a * 31;
            if (h == 0) {
                hashIsZero = true;
            } else {
                hash = h;
            }
        }
        return h;
    }
}

// A boxed cache, and a cache whose initial value is a named constant.
@Immutable class OtherSentinels {
    private static final int NOT_COMPUTED = -2;

    final int a;
    private @Assignable @Derived Integer size;
    private @Assignable @Derived int index = NOT_COMPUTED;

    OtherSentinels(int a) {
        this.a = a;
    }

    int size() {
        Integer result = size;
        if (result == null) {
            result = size = a * 2;
        }
        return result.intValue();
    }

    int index() {
        int local = index;
        if (local == NOT_COMPUTED) {
            index = local = a + 1;
        }
        return local;
    }

    // Zero is not the initial value of this field, so comparing with it proves nothing.
    int indexComparedWithZero() {
        int local = index;
        if (local != 0) {
            // :: error: (derived.field.result.unchecked)
            return local;
        }
        // :: error: (derived.field.result.branch)
        return a + 1;
    }
}
