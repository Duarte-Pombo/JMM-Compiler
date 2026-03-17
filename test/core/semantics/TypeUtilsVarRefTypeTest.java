package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeUtilsVarRefTypeTest extends JmmTestEnv {

    public TypeUtilsVarRefTypeTest() {
        super("", "");
    }

    @Test
    public void getExprTypeVarRefResolvesLocalBeforeField() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    int v;
                    public int m() {
                        int v;
                        return v;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "v");
        var type = new TypeUtils(semantics.getSymbolTable()).getExprType(varRef);

        assertEquals("Local variable v should resolve to int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeVarRefResolvesParameterBeforeField() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    boolean p;
                    public int m(int p) {
                        return p;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "p");
        var type = new TypeUtils(semantics.getSymbolTable()).getExprType(varRef);

        assertEquals("Parameter p should resolve to int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeVarRefResolvesFieldWhenNoLocalOrParam() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    int f;
                    public int m() {
                        return f;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "f");
        var type = new TypeUtils(semantics.getSymbolTable()).getExprType(varRef);

        assertEquals("Field f should resolve to int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeVarRefResolvesCurrentClassAsStaticReference() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int foo() { return 0; }
                    public int m() {
                        A.foo();
                        return 0;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "A");
        var type = new TypeUtils(semantics.getSymbolTable()).getExprType(varRef);

        assertTrue("A should resolve to class type", type.isClass());
        assertEquals("A should resolve to x.A", "x.A", type.asClass().fullyQualifiedName());
        assertTrue("A should resolve as static reference", type.asClass().staticRef());
    }

    @Test
    public void getExprTypeVarRefResolvesImportedClassAsStaticReference() {
        var semantics = symbolTableFromSnippet("""
                package x;
                import util.io;
                class A {
                    public int m() {
                        io.read();
                        return 0;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "io");
        var type = new TypeUtils(semantics.getSymbolTable()).getExprType(varRef);

        assertTrue("io should resolve to class type", type.isClass());
        assertEquals("io should resolve to util.io", "util.io", type.asClass().fullyQualifiedName());
        assertTrue("io should resolve as static reference", type.asClass().staticRef());
    }

    @Test
    public void getExprTypeVarRefMissingSymbolThrowsError() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        return z;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "z");
        var types = new TypeUtils(semantics.getSymbolTable());

        try {
            types.getExprType(varRef);
            fail("Expected RuntimeException for missing variable reference");
        } catch (RuntimeException e) {
            assertTrue("Expected missing variable message", e.getMessage().contains("z"));
        }
    }

    @Test
    public void getExprTypeVarRefKeepsMultidimensionalArrayDimensions() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int[][] m() {
                        int[][] matrix;
                        return matrix;
                    }
                }""", false);

        var varRef = findVarRef(semantics.getRootNode(), "matrix");
        var type = new TypeUtils(semantics.getSymbolTable()).getExprType(varRef);

        assertTrue("matrix should resolve to an array type", type.isArray());
        assertEquals("matrix should resolve to int[][]", new JmmArrayType(JmmPrimitiveType.INT, 2), type);
    }

    private JmmNode findVarRef(JmmNode root, String name) {
        return root.getDescendants(JmmKind.VAR_REF_EXPR).stream()
                .filter(node -> name.equals(node.get("name")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find VarRefExpr for '" + name + "'"));
    }
}
