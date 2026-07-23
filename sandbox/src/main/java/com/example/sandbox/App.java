package com.example.sandbox;

/** The application entry point — prints a computation so `gradle run` shows the built code executing. */
public final class App {

    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println("kontinuance-sandbox: 2 + 3 = " + calc.add(2, 3));
        System.out.println("kontinuance-sandbox: 4 * 5 = " + calc.multiply(4, 5));
    }
}
