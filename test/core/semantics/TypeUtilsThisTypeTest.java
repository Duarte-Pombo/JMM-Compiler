package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeUtilsThisTypeTest extends JmmTestEnv {

    public TypeUtilsThisTypeTest() {
        super("", "");
    }

    @Test
    public void getExprTypeThisReturnsCurrentClassType() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() { return 0; }
                }""", false).getSymbolTable();

        var expr = parseExpr("this");
        var type = new TypeUtils(table).getExprType(expr);

        assertTrue("Expected 'this' to be a class type", type.isClass());
        assertEquals("Expected current class fq name", "x.A", type.asClass().fullyQualifiedName());
        assertTrue("Expected imported flag to be false", !type.asClass().isImported());
        assertTrue("Expected staticRef flag to be false", !type.asClass().staticRef());
    }

    private JmmNode parseExpr(String code) {
        var parser = parseSnippet(code, JmmKind.EXPR);
        var expr = parser.rootNode();
        assertTrue("Parsed expression should be This", expr.isInstance(JmmKind.THIS));
        return expr;
    }
}
