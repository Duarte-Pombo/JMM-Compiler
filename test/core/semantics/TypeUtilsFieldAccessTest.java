package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeUtilsFieldAccessTest extends JmmTestEnv {

    public TypeUtilsFieldAccessTest() {
        super("", "");
    }

    @Test
    public void getExprTypeFieldAccessLengthReturnsIntForArrayReceiver() {
        var parser = parseSnippet("arr.length", JmmKind.EXPR);
        var fieldAccess = parser.rootNode();
        var receiver = fieldAccess.getChild(0);

        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    public int m() { return 0; }
                }""", false);

        var types = new ReceiverTypeAwareTypeUtils(
                semantics.getSymbolTable(),
                receiver,
                new JmmArrayType(JmmPrimitiveType.INT, 1)
        );

        var type = types.getExprType(fieldAccess);

        assertEquals("arr.length should have type int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeFieldAccessExistingClassFieldReturnsFieldType() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    int fieldInt;
                    public int m() {
                        int y;
                        y = obj.fieldInt;
                        return y;
                    }
                }""", false);

        var root = semantics.getRootNode();
        var fieldAccess = findFieldAccess(root, "fieldInt");
        var receiver = fieldAccess.getChild(0);
        var receiverType = new JmmClassType(semantics.getSymbolTable().getFullyQualifiedName(), false, false);

        var types = new ReceiverTypeAwareTypeUtils(semantics.getSymbolTable(), receiver, receiverType);
        var type = types.getExprType(fieldAccess);

        assertEquals("obj.fieldInt should have type int", JmmPrimitiveType.INT, type);
    }

    @Test
    public void getExprTypeFieldAccessMissingClassFieldThrowsError() {
        var semantics = symbolTableFromSnippet("""
                package x;
                class A {
                    int fieldInt;
                    public int m() {
                        int y;
                        y = obj.unknownField;
                        return y;
                    }
                }""", false);

        var root = semantics.getRootNode();
        var fieldAccess = findFieldAccess(root, "unknownField");
        var receiver = fieldAccess.getChild(0);
        var receiverType = new JmmClassType(semantics.getSymbolTable().getFullyQualifiedName(), false, false);

        var types = new ReceiverTypeAwareTypeUtils(semantics.getSymbolTable(), receiver, receiverType);

        try {
            types.getExprType(fieldAccess);
            fail("Expected RuntimeException for missing field access");
        } catch (RuntimeException e) {
            assertTrue("Expected missing field message", e.getMessage().contains("unknownField"));
        }
    }

    private JmmNode findFieldAccess(JmmNode root, String fieldName) {
        return root.getDescendants(JmmKind.FIELD_ACCESS).stream()
                .filter(node -> fieldName.equals(node.get("name")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find FieldAccess for '" + fieldName + "'"));
    }

    private static class ReceiverTypeAwareTypeUtils extends TypeUtils {
        private final JmmNode receiverNode;
        private final JmmType receiverType;

        ReceiverTypeAwareTypeUtils(pt.up.fe.comp.jmm.analysis.table.SymbolTable table, JmmNode receiverNode, JmmType receiverType) {
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
