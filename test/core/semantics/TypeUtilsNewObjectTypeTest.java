package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeUtilsNewObjectTypeTest extends JmmTestEnv {

    public TypeUtilsNewObjectTypeTest() {
        super("", "");
    }

    @Test
    public void getExprTypeNewObjectImportedClassUsesImportedFqName() {
        var table = symbolTableFromSnippet("""
                package x;
                import util.io;
                class A {
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var expr = parseExpr("new io()");
        var type = new TypeUtils(table).getExprType(expr);

        assertTrue("Expected new io() type to be a class", type.isClass());
        assertEquals("Expected imported class fq name", "util.io", type.asClass().fullyQualifiedName());
        assertTrue("Expected imported flag to be true", type.asClass().isImported());
        assertTrue("Expected staticRef flag to be false", !type.asClass().staticRef());
    }

    @Test
    public void getExprTypeNewObjectCurrentClassUsesCurrentClassFqName() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var expr = parseExpr("new A()");
        var type = new TypeUtils(table).getExprType(expr);

        assertTrue("Expected new A() type to be a class", type.isClass());
        assertEquals("Expected current class fq name", "x.A", type.asClass().fullyQualifiedName());
        assertTrue("Expected imported flag to be false", !type.asClass().isImported());
        assertTrue("Expected staticRef flag to be false", !type.asClass().staticRef());
    }

    @Test
    public void getExprTypeNewObjectUnknownClassFallsBackToSimpleName() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var expr = parseExpr("new B()");
        var type = new TypeUtils(table).getExprType(expr);

        assertTrue("Expected new B() type to be a class", type.isClass());
        assertEquals("Expected unknown class to keep simple name", "B", type.asClass().fullyQualifiedName());
        assertTrue("Expected imported flag to be false", !type.asClass().isImported());
        assertTrue("Expected staticRef flag to be false", !type.asClass().staticRef());
    }

    private JmmNode parseExpr(String code) {
        var parser = parseSnippet(code, JmmKind.EXPR);
        JmmNode expr = parser.rootNode();
        assertTrue("Parsed expression should be NewObject", expr.isInstance(JmmKind.NEW_OBJECT));
        return expr;
    }
}
