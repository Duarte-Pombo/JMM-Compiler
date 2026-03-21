package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeUtilsMethodCallTypeTest extends JmmTestEnv {

    public TypeUtilsMethodCallTypeTest() {
        super("", "");
    }

    @Test
    public void getExprTypeMethodCallExistingReturnsMethodReturnType() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int foo(int x) { return x; }
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var methodCall = parseMethodCallExpr("this.foo(1)");
        var type = new TypeUtils(table).getExprType(methodCall);

        assertEquals("this.foo(1) should have type int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeMethodCallMissingMethodThrowsError() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int foo(int x) { return x; }
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var methodCall = parseMethodCallExpr("this.bar(1)");
        var types = new TypeUtils(table);

        try {
            types.getExprType(methodCall);
            fail("Expected RuntimeException for missing method call");
        } catch (RuntimeException e) {
            assertTrue("Expected missing method message", e.getMessage().contains("bar"));
        }
    }

    @Test
    public void getExprTypeMethodCallOverloadSelectsCorrectSignature() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int foo(int x) { return x; }
                    public boolean foo(boolean b) { return b; }
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var types = new TypeUtils(table);

        var intCall = parseMethodCallExpr("this.foo(1)");
        var boolCall = parseMethodCallExpr("this.foo(true)");

        assertEquals("this.foo(1) should resolve to int overload", JmmPrimitiveType.INT, types.getExprType(intCall));
        assertEquals("this.foo(true) should resolve to boolean overload", JmmPrimitiveType.BOOLEAN, types.getExprType(boolCall));
    }

    @Test
    public void getExprTypeMethodCallInheritedFromObjectResolvesThroughSuper() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var methodCall = parseMethodCallExpr("this.toString()");
        var type = new TypeUtils(table).getExprType(methodCall);

        assertTrue("this.toString() should have class type", type.isClass());
        assertTrue("this.toString() should have type String",
                type.asClass().fullyQualifiedName().endsWith("String"));
    }

    private JmmNode parseMethodCallExpr(String code) {
        var parser = parseSnippet(code, JmmKind.EXPR);
        var expr = parser.rootNode();
        assertTrue("Parsed expression should be MethodCall", expr.isInstance(JmmKind.METHOD_CALL));
        return expr;
    }
}
