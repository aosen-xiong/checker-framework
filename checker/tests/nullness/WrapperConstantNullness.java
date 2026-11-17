// @below-java17-jdk-skip-test
// Each numeric constant ignores its lookup argument and resolves to itself.

import org.checkerframework.framework.qual.AnnotatedFor;

@AnnotatedFor("nullness")
class WrapperConstantNullness {
    double resolve(Integer i, Long l, Float f, Double d) {
        Integer ri = i.resolveConstantDesc(null);
        Long rl = l.resolveConstantDesc(null);
        Float rf = f.resolveConstantDesc(null);
        Double rd = d.resolveConstantDesc(null);
        return ri + rl + rf + rd;
    }

    int describe(Boolean b, Byte y, Short s, Integer i, Long l, Float f, Double d) {
        return b.describeConstable().toString().length()
                + y.describeConstable().toString().length()
                + s.describeConstable().toString().length()
                + i.describeConstable().toString().length()
                + l.describeConstable().toString().length()
                + f.describeConstable().toString().length()
                + d.describeConstable().toString().length();
    }
}
