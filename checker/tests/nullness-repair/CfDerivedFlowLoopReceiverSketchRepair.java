// @repair-kind: dereference.of.nullable
// @repair-mode: sketch
// @repair-plan-kind: ADD_NULL_CHECK
// @repair-plan-risk: BODY_CHANGE
// @repair-plan-automatic: false
// @repair-plan-edits: 0
public class CfDerivedFlowLoopReceiverSketchRepair {
    void simpleWhileLoop() {
        String s = "m";

        while (s != null) {
            s.toString();
            s = null;
        }
        // :: error: (dereference.of.nullable)
        s.toString();
    }
}
