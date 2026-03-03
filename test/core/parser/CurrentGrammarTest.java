package core.parser;

import org.junit.Test;
import pt.up.fe.comp.test.env.JmmTestEnv;

public class CurrentGrammarTest extends JmmTestEnv {

    public CurrentGrammarTest() {
        super("", "");
    }

    @Test
    public void testMinimalProgram() {
        var code = """
            package my.minimal.pkg;
            class EmptyClass {
            }""";
        parseSnippet(code);
    }

    @Test
    public void testImportsAndExtends() {
        var code = """
            package a.b.c;
            import java.util.Scanner;
            import io.Printer;
            import singleWordImport;
            
            class Child extends Parent {
            }""";
        parseSnippet(code);
    }

    @Test
    public void testFieldDeclarations() {
        var code = """
            package test;
            class FieldTest {
                int a;
                boolean b;
                void c;
                MyObject obj;
                int[] arr;
                boolean[][] multiArr;
            }""";
        parseSnippet(code);
    }

    @Test
    public void testMethodSignatures() {
        var code = """
            package test;
            class MethodTest {
                
                void emptyMethod() {}
                
                public int publicMethod(int a) { return a; }
                
                static boolean staticMethod(int a, boolean b, MyObj c) { return b; }
                
                public static int[] mainMethod(String[] args) { return args; }
            }""";
        parseSnippet(code);
    }

    @Test
    public void testVariablesAndStatementsOrdering() {
        var code = """
            package test;
            class OrderTest {
                void testOrder() {
                    int a;
                    int b;
                    
                    a = 1;
                    b = 2;
                    return a;
                }
            }""";
        parseSnippet(code);
    }

    @Test
    public void testExpressionsAndPrecedence() {
        var code = """
            package test;
            class ExprTest {
                void mathTest() {
                    int a;
                    int b;
                    
                    a = 1 + 2 * 3 - 4 / 2;
                    
                    b = a.doSomething();
                    
                    a.getB().getC(1, a);
                    
                    return a * b;
                }
            }""";
        parseSnippet(code);
    }

    // --- NEW TESTS ADDED BELOW ---

    @Test
    public void testIfWithoutElse() {
        // Tests the extension feature: an if statement without an else branch
        var code = """
            package test;
            class IfTest {
                void testIf() {
                    int a;
                    a = 1;
                    
                    if (a) 
                        a = 2;
                        
                    return a ;
                }
            }""";
        parseSnippet(code);
    }

    @Test
    public void testIfElseAndCompoundStatements() {
        // Tests standard if-else blocks paired with compound {} statements
        var code = """
            package test;
            class IfElseTest {
                void testIfElse() {
                    int a;
                    int b;
                    
                    if (a) {
                        a = 1;
                        b = 2;
                    } else {
                        a = 3;
                        b = 4;
                    }
                }
            }""";
        parseSnippet(code);
    }

    @Test
    public void testNestedStatements() {
        // Tests deeply nested curly braces and nested if-statements
        var code = """
            package test;
            class NestedTest {
                void testNesting() {
                    int a;
                    
                    {
                        {
                            a = 1;
                            
                            if (a) {
                                if (a) 
                                    a = 2;
                                else {
                                    a = 3;
                                }
                            }
                        }
                    }
                }
            }""";
        parseSnippet(code);
    }
}