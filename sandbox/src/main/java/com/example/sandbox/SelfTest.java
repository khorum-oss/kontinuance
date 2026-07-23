package com.example.sandbox;

/**
 * A zero-dependency stand-in for a test suite: it runs assertions over {@link Calculator} and exits non-zero
 * if any fail, so the Kontinuance `test` step turns red on a real regression. Output is plain ASCII so it
 * reads cleanly in the run's log panel. Set {@code FAIL_SANDBOX=true} to force a failing run (to demo the
 * red path end to end).
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        Calculator calc = new Calculator();
        boolean forceFail = "true".equalsIgnoreCase(System.getenv("FAIL_SANDBOX"));

        System.out.println("running CalculatorTest");
        check("add returns the sum", calc.add(2, 3) == 5);
        check("subtract returns the difference", calc.subtract(9, 4) == 5);
        check("multiply returns the product", calc.multiply(4, 5) == (forceFail ? 999 : 20));

        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }
}
