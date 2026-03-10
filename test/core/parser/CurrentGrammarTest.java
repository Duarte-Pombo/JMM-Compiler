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
                public static void main(String[] args) { return args; }
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

    @Test
    public void testIfWithoutElse() {
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

    @Test
    public void testEmptyWhile() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    int a;
                    while (a){
                    }
                    return a ;
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void testWhileStmt() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    int a;
                    while (a + 1){
                        a = a + 1;
                    }
                    return a ;
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void testNestedWhile() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    int a;
                    while (a){
                        while(a) {}
                    }
                    return a ;
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void testWhileWithoutBrackets() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    int a;
                    while (a)
                        a = a + 1;
                    return a ;
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void invalidStmtWhile() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    while () { }
                }
            }
            """;
        parseSnippetWithErrors(code);
    }

    @Test
    public void invalidWhile() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    while a { }
                }
            }
            """;
        parseSnippetWithErrors(code);
    }

    @Test
    public void noBodyWhile() {
        var code = """
            package test;
            class WhileTest {
                void testWhile() {
                    while (a)
                }
            }
            """;
        parseSnippetWithErrors(code);
    }

    @Test
    public void newObject() {
        var code = """
            package test;
            class newObject {
                void testNew() {
                    A a;
                    a = new A();
                    a.b();
                    a.b(c.d());
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void callNew() {
        var code = """
            package test;
            class callNew {
                void testNew() {
                    new A().b().c();
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void callArgs() {
        var code = """
            package test;
            class callArgs {
                void testCall() {
                    a.b(1, 2, 3);
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void callExpr() {
        var code = """
            package test;
            class callExpr {
                void testCall() {
                    (1 + 2).a();
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void testVoidReturnWithoutExpression() {
        var code = """
            package test;
            class ReturnVoid {
                void foo() {
                    return;
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void testNonVoidReturnWithExpression() {
        var code = """
            package test;
            class ReturnInt {
                int foo() {
                    return 1;
                }
            }
            """;
        parseSnippet(code);
    }

    @Test
    public void testMainMethodEmpty() {
        var code = """
                package test;
                class Test {
                    public static void main(String[] args) {}
                }
            """;
        parseSnippet(code);
    }

    @Test
    public void newArrays() {
        var code = """
        package test;
        class ArrayTest {
            void testArray() {
                int[] a;
                a = new int[10];
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void storeArray() {
        var code = """
        package test;
        class ArrayStoreTest {
            void testArray() {
                int[] a;
                a = new int[5];
                a[0] = 42;
                a[1] = 1 + 2;
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void loadArray() {
        var code = """
        package test;
        class ArrayLoadTest {
            void testArray() {
                int[] a;
                int x;
                a = new int[5];
                a[0] = 10;
                x = a[0];
                x = a[1] + 2;
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void nestedArray() {
        var code = """
        package test;
        class NestedArrayTest {
            void testArray() {
                int[] a;
                int[] b;
                int x;
                a = new int[5];
                b = new int[5];
                a[0] = 1;
                b[0] = a[0] + 2;
                x = b[a[0]];
            }
        }
        """;
        parseSnippet(code);
    }
    @Test
    public void arrayMethodCall() {
        var code = """
        package test;
        class ArrayMethodTest {
            void testArray() {
                int[] a;
                a = new int[3];
                ArrayMethodTest.print(a[0]);
                ArrayMethodTest.foo(a[1]);
            }
            void print(int x) {}
            void foo(int y) {}
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testLengthUsage() {
        var code = """
        package test;
        class LengthTest {
            void testLength() {
                int[] a;
                int x;
                a = new int[3];
                x = a.length;
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testArrayAccessUsage() {
        var code = """
        package test;
        class ArrayAccessUsageTest {
            void testAccess() {
                int[] a;
                int x;
                a = new int[4];
                x = a[0];
                x = a[1] + a[2];
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testNegationUsage() {
        var code = """
        package test;
        class NegationTest {
            void testNeg() {
                boolean b;
                b = !false;
                b = !b;
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testNewArrayUsage() {
        var code = """
        package test;
        class NewArrayUsageTest {
            void testNewArray() {
                int[] a;
                a = new int[10];
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testBinaryAndOrUsage() {
        var code = """
        package test;
        class LogicOpsTest {
            void testLogic() {
                boolean b;
                b = true && false;
                b = b || true;
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testArrayLiteralUsage() {
        var code = """
        package test;
        class ArrayLiteralTest {
            void testArrayLiteral() {
                int[] a;
                a = [1, 2, 3];
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testBooleanLiteralsUsage() {
        var code = """
        package test;
        class BooleanLiteralsTest {
            void testBooleans() {
                boolean b = false;
                b = true;
                b = false;
            }
        }
        """;
        parseSnippet(code);
    }

    @Test
    public void testThisUsage() {
        var code = """
        package test;
        class ThisTest {
            ThisTest self;
            void foo() {}
            void testThis() {
                self = this;
                this.foo();
            }
        }
        """;
        parseSnippet(code);
    }
}
