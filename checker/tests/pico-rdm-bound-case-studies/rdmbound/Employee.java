package rdmbound;

import org.checkerframework.checker.mutability.qual.Mutable;
import org.checkerframework.checker.mutability.qual.Readonly;
import org.checkerframework.checker.mutability.qual.ReceiverDependentMutable;

/** An employee whose salary can change, unless the record is immutable. */
@ReceiverDependentMutable public class Employee {
    private final String name;
    private int salary;

    public Employee(String name, int salary) {
        this.name = name;
        this.salary = salary;
    }

    public String name(@Readonly Employee this) {
        return name;
    }

    public int salary(@Readonly Employee this) {
        return salary;
    }

    public void raise(@Mutable Employee this, int amount) {
        salary += amount;
    }
}
