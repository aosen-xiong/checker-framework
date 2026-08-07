import org.checkerframework.checker.region.qual.Region;
import org.checkerframework.common.aliasing.qual.Unique;

// More validation need to do, need to check whether "#1" is actuall arena type
// TODO METHOD AND CLASS REGION PARAMTER
@SuppressWarnings("cast.unsafe.constructor.invocation")
class Test {

    // Used as ghost/holder class later on
    class UniquityRegion {
        @Unique UniquityRegion() {}
    }

    class UniquityDemo {
        final /*@Unique*/ UniquityRegion reg;
        @Region("reg") Object obj;

        UniquityDemo(UniquityRegion reg) {
            this.reg = reg;
            this.obj = new @Region("this.reg") Object();
        }

        // access path should be final and the terminal should arena type
        void update(@Region("this.reg") Object obj) {
            this.obj = obj;
        }
    }

    void m() {
        // Create regions
        final UniquityRegion reg1 = new UniquityRegion();

        // Create the demo obj
        UniquityDemo demo = new UniquityDemo(reg1);

        // Allocate objects in the regions
        Object obj1 = new @Region("demo.reg") Object();
        Object obj2 = new Object();

        // Valid update: obj1 is in the correct region
        demo.update(obj1);

        // Invalid update: obj2 is in the wrong region
        // :: error: (argument.type.incompatible)
        demo.update(obj2);
    }
}
