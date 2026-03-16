package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeUtilsArrayAccessTest extends JmmTestEnv {

    public TypeUtilsArrayAccessTest() {
        super("", "");
    }

    @Test
    public void arrayIntAccess() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        int x;
                        a = new int[5];
                        x = a[0];
                        return x;
                    }
                }""", false).getSymbolTable();

        var root = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        int x;
                        a = new int[5];
                        x = a[0];
                        return x;
                    }
                }""", false).getRootNode();

        var arrayAccess = findArrayAccess(root, "a", "0");
        var type = new TypeUtils(semantics).getExprType(arrayAccess);

        assertEquals("a[0] should have type int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeArrayAccessOnMultidimensionalArrayReducesDimension() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int y;
                        int x;
                        y = matrix[1];
                        x = 0;
                        return x;
                    }
                }""", false);

        var table = semantics.getSymbolTable();
        var root = semantics.getRootNode();
        var arrayAccess = findArrayAccess(root, "matrix", "1");
        var receiver = arrayAccess.getChild(0);

        var types = new ReceiverTypeAwareTypeUtils(table, receiver, new JmmArrayType(JmmPrimitiveType.INT, 2));
        var type = types.getExprType(arrayAccess);

        assertTrue("matrix[1] should still be an array", type.isArray());
        assertEquals("matrix[1] should reduce to int[]", new JmmArrayType(JmmPrimitiveType.INT, 1), type);
    }

    @Test
    public void arrayAccessError() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int x;
                        int y;
                        y = x[0];
                        x = 0;
                        return x;
                    }
                }""", false);

        var table = semantics.getSymbolTable();
        var root = semantics.getRootNode();
        var arrayAccess = findArrayAccess(root, "x", "0");
        var receiver = arrayAccess.getChild(0);

        var types = new ReceiverTypeAwareTypeUtils(table, receiver, JmmPrimitiveType.INT);

        try {
            types.getExprType(arrayAccess);
            fail("Expected RuntimeException for non-array receiver");
        } catch (RuntimeException e) {
            assertTrue("Expected non-array receiver message", e.getMessage().contains("not an array"));
        }
    }

    @Test
    public void typeArrayAccessError() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int y;
                        y = a[true];
                        int x;
                        x = 0;
                        return x;
                    }
                }""", false);

        var table = semantics.getSymbolTable();
        var root = semantics.getRootNode();
        var arrayAccess = findArrayAccess(root, "a", "true");
        var receiver = arrayAccess.getChild(0);

        var types = new ReceiverTypeAwareTypeUtils(table, receiver, new JmmArrayType(JmmPrimitiveType.INT, 1));

        try {
            types.getExprType(arrayAccess);
            fail("Expected RuntimeException for non-int array index");
        } catch (RuntimeException e) {
            assertTrue("Expected non-int index message", e.getMessage().contains("Array index must be int"));
        }
    }

    @Test
    public void newArrayDimensions() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int x;
                        x = 0;
                        return x;
                    }
                }""", false).getSymbolTable();

        var newArrayExpr = parseExpr("new int[10][20]");
        var type = new TypeUtils(table).getExprType(newArrayExpr);

        assertEquals("new int[10][20] should have type int[][]", new JmmArrayType(JmmPrimitiveType.INT, 2), type);
    }

    @Test
    public void newArrayByExtension() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int x;
                        x = 0;
                        return x;
                    }
                }""", false).getSymbolTable();

        var newArrayExpr = parseExpr("new int[] {1, 2, 3}");
        var type = new TypeUtils(table).getExprType(newArrayExpr);

        assertEquals("new int[] {1, 2, 3} should have type int[]", new JmmArrayType(JmmPrimitiveType.INT, 1), type);
    }


    @Test
    public void nestedArrayAccess() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int x;
                        x = matrix[1][2];
                        return x;
                    }
                }""", false);

        var table = semantics.getSymbolTable();
        var root = semantics.getRootNode();
        var expr = findNestedArrayAccess(root, "matrix", "1", "2");
        var matrixRef = expr.getChild(0).getChild(0);

        var types = new ReceiverTypeAwareTypeUtils(table, matrixRef, new JmmArrayType(JmmPrimitiveType.INT, 2));
        var type = types.getExprType(expr);

        assertEquals("matrix[1][2] should have type int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void newArrayError() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int x;
                        x = 0;
                        return x;
                    }
                }""", false).getSymbolTable();

        var expr = parseExpr("new int[true]");

        try {
            new TypeUtils(table).getExprType(expr);
            fail("Expected RuntimeException for non-int size");
        } catch (RuntimeException e) {
            assertTrue("erm",e.getMessage().contains("Array size must be int"));
        }
    }

    @Test
    public void newSingleArrayReturnsIntArray() {
        var table = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int x;
                        x = 0;
                        return x;
                    }
                }""", false).getSymbolTable();

        var expr = parseExpr("new int[10]");
        var type = new TypeUtils(table).getExprType(expr);

        assertEquals("erm",new JmmArrayType(JmmPrimitiveType.INT, 1), type);
    }

    @Test
    public void arrayAccessWithExpressionIndex() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        int x;
                        a = new int[5];
                        x = a[1 + 2];
                        return x;
                    }
                }""", false);

        var table = semantics.getSymbolTable();
        var root = semantics.getRootNode();
        var expr = findArrayAccessWithBinaryIndex(root, "a", "+");
        var type = new TypeUtils(table).getExprType(expr);

        assertEquals("a[1 + 2] should have type int", JmmPrimitiveType.INT, type);
    }

    private JmmNode parseExpr(String code) {
        return parseSnippet(code, JmmKind.EXPR).rootNode();
    }

    private JmmNode parseArrayAccessExpr(String code) {
        var expr = parseExpr(code);
        assertTrue("Parsed expression should be ArrayAccess", expr.isInstance(JmmKind.ARRAY_ACCESS));
        return expr;
    }

    private JmmNode findArrayAccess(JmmNode root, String receiverName, String indexRepr) {
        return root.getDescendants(JmmKind.ARRAY_ACCESS).stream()
                .filter(node -> node.getChild(0).isInstance(JmmKind.VAR_REF_EXPR))
                .filter(node -> receiverName.equals(node.getChild(0).get("name")))
                .filter(node -> indexMatches(node.getChild(1), indexRepr))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find ArrayAccess for '" + receiverName + "[" + indexRepr + "]'"));
    }

    private JmmNode findNestedArrayAccess(JmmNode root, String receiverName, String firstIndex, String secondIndex) {
        return root.getDescendants(JmmKind.ARRAY_ACCESS).stream()
                .filter(node -> node.getChild(0).isInstance(JmmKind.ARRAY_ACCESS))
                .filter(node -> indexMatches(node.getChild(1), secondIndex))
                .filter(node -> {
                    var inner = node.getChild(0);
                    return inner.getChild(0).isInstance(JmmKind.VAR_REF_EXPR)
                            && receiverName.equals(inner.getChild(0).get("name"))
                            && indexMatches(inner.getChild(1), firstIndex);
                })
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find nested ArrayAccess for '" + receiverName + "[" + firstIndex + "][" + secondIndex + "]'"));
    }

    private JmmNode findArrayAccessWithBinaryIndex(JmmNode root, String receiverName, String operator) {
        return root.getDescendants(JmmKind.ARRAY_ACCESS).stream()
                .filter(node -> node.getChild(0).isInstance(JmmKind.VAR_REF_EXPR))
                .filter(node -> receiverName.equals(node.getChild(0).get("name")))
                .filter(node -> node.getChild(1).isInstance(JmmKind.BINARY_EXPR))
                .filter(node -> operator.equals(node.getChild(1).get("op")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find ArrayAccess for '" + receiverName + "[<binary expr>]'") );
    }

    private boolean indexMatches(JmmNode indexNode, String repr) {
        if (indexNode.isInstance(JmmKind.INTEGER_LITERAL)) {
            return repr.equals(indexNode.get("value"));
        }

        if (indexNode.isInstance(JmmKind.BOOLEAN_LITERAL)) {
            return repr.equals(indexNode.get("value"));
        }

        return false;
    }

    private static class ReceiverTypeAwareTypeUtils extends TypeUtils {
        private final JmmNode receiverNode;
        private final JmmType receiverType;

        ReceiverTypeAwareTypeUtils(SymbolTable table, JmmNode receiverNode, JmmType receiverType) {
            super(table);
            this.receiverNode = receiverNode;
            this.receiverType = receiverType;
        }

        @Override
        public JmmType getExprType(JmmNode expr) {
            if (expr == receiverNode) {
                return receiverType;
            }
            return super.getExprType(expr);
        }
    }
}
