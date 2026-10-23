import java.lang.foreign.Arena;

public class Main {

    public static void main(String[] args) throws Throwable {
        LargeArray<Point> outerarray;
        try (var outerarena = Arena.ofConfined()) {
            try (var innerarena = Arena.ofConfined()) {
                var innerarray = new /* @Region("innerarena") */ LargeArray<Point>(innerarena, 10L, Point.DESCRIPTOR);
                for (long i = 0; i < innerarray.length(); i++) {
                    var point = new /* @Region("innerarena") */ Point(innerarena);
                    point.setX((int) i);
                    point.setY((int) i * 2);
                    innerarray.set(i, point);
                }

                outerarray = new /* @Region("outerarena") */ LargeArray<Point>(outerarena, 10L, Point.DESCRIPTOR);
                for (long i = 0; i < outerarray.length(); i++) {
                    var point = new /* @Region("outerarena") */ Point(outerarena);
                    point.setX((int) i);
                    point.setY((int) i * 2);
                    outerarray.set(i, point);
                }

                // show modification of element in array
//                /* @Region("arena") Point*/ var midPoint = array.get(5L);
//                midPoint.setX(42);
//                midPoint.setY(117);
//                midPoint.swap();

                // print array contents
//                for (long i = 0; i < array.length(); i++) {
//                    System.out.printf("array[%d] = %s%n", i, array.get(i));
//                }
            }
        }
        System.out.printf("array[%d] = %s%n", 0, outerarray.get(0));
    }
}