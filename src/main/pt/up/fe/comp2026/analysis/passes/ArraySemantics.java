package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;

public class ArraySemantics extends AnalysisVisitor {
    @Override
    protected void buildVisitor() {
        addVisit(JmmKind.NEW_ARRAY_BY_EXTENSION, this::visitNewArrayByExtension);
        addVisit(JmmKind.NEW_ARRAY, this::visitNewArray);
        addVisit(JmmKind.ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(JmmKind.ARRAY_ACCESS, this::visitArrayAccess);
    }

    private Void visitNewArrayByExtension(JmmNode newArrayByExtension, SymbolTable table) {
        var types = TypeUtils.with(table);

        var arrayInitNodes = newArrayByExtension.getChildren(JmmKind.ARRAY_INIT);
        if (arrayInitNodes.isEmpty()) {
            return null;
        }

        validateArrayInitializer(arrayInitNodes.getFirst(), types.getExprType(newArrayByExtension), types);
        return null;
    }

    private void validateArrayInitializer(JmmNode arrayInit, JmmType expectedArrayType, TypeUtils types) {
        if (!expectedArrayType.isArray()) {
            addReport(newError(arrayInit, "Array initializer cannot be assigned to non-array type '" +
                    expectedArrayType.print() + "'."));
            return;
        }

        var expectedElementType = peelArrayDimension((JmmArrayType) expectedArrayType);

        for (JmmNode arrayElem : arrayInit.getChildren(JmmKind.ARRAY_ELEM)) {
            if (arrayElem.getChildren().isEmpty()) continue;

            JmmNode expr = arrayElem.getChild(0);
            if (expr.isInstance(JmmKind.ARRAY_INIT)) {
                validateArrayInitializer(expr, expectedElementType, types);
                continue;
            }

            var exprType = types.getExprType(expr);

            if (!types.isAssignable(exprType, expectedElementType)) {

                var message = "Type mismatch in array initializer. Expected '" + expectedElementType.print() +
                              "' but got '" + exprType.print() + "'.";

                addReport(newError(expr, message));
            }
        }
    }

    private JmmType peelArrayDimension(JmmArrayType arrayType) {
        int dims = arrayType.dimension();
        return dims > 1
                ? new JmmArrayType(arrayType.itemType(), dims - 1)
                : arrayType.itemType();
    }

    private Void visitNewArray(JmmNode newArray, SymbolTable table) {
        var types = TypeUtils.with(table);
        var expectedType = TypeUtils.intType();

        for (JmmNode expr : newArray.getChildren()) {
            var exprType = types.getExprType(expr);

            if (!types.isAssignable(exprType, expectedType)) {
                var message = "Array dimension size must be an integer. Got '" + exprType.print() + "'.";
                addReport(newError(expr, message));
            }
        }
        return null;
    }

    private Void visitArrayAssignStmt(JmmNode arrayAssignStmt, SymbolTable table) {
        var types = TypeUtils.with(table);
        var expectedIndexType = TypeUtils.intType();

        String arrayName = arrayAssignStmt.get("var");
        var arrayTypeOpt = types.getVariableType(arrayName, arrayAssignStmt);

        if (arrayTypeOpt.isEmpty() || !arrayTypeOpt.get().isArray()) {
            addReport(newError(arrayAssignStmt, "Variable '" + arrayName + "' is not an array."));
            return null;
        }

        var currentType = arrayTypeOpt.get();
        int numIndices = arrayAssignStmt.getNumChildren() - 1;

        for (int i = 0; i < numIndices; i++) {
            var indexExpr = arrayAssignStmt.getChild(i);
            var indexType = types.getExprType(indexExpr);

            if (!types.isAssignable(indexType, expectedIndexType)) {
                addReport(newError(indexExpr, "Array access index must be an integer. Got '" + indexType.print() + "'."));
            }

            if (currentType.isArray()) {
                var arrType = (pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType) currentType;
                int dims = arrType.dimension();
                if (dims > 1) {
                    currentType = new pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType(arrType.itemType(), dims - 1);
                } else {
                    currentType = arrType.itemType();
                }
            } else {
                addReport(newError(arrayAssignStmt, "Trying to access more dimensions than the array has."));
                return null;
            }
        }

        var expectedElementType = currentType;
        var assignedValueExpr = arrayAssignStmt.getChild(numIndices);
        var assignedValueType = types.getExprType(assignedValueExpr);

        if (!types.isAssignable(assignedValueType, expectedElementType)) {
            var message = "Cannot assign type '" + assignedValueType.print() + "' to '" + expectedElementType.print() + "'.";
            addReport(newError(assignedValueExpr, message));
        }

        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table) {
        var types = TypeUtils.with(table);
        var expectedType = TypeUtils.intType();

        var array = arrayAccess.getChild(0);
        var arrayType = types.getExprType(array);

        if (!arrayType.isArray()) {
            addReport(newError(array, "Array access is done over a non-array type."));
            return null;
        }

        var idx_expr = arrayAccess.getChild(1);
        var idxType = types.getExprType(idx_expr);

        if (!types.isAssignable(idxType, expectedType)) {
            addReport(newError(idx_expr, "Array access index must be an integer. Got '" + idxType.print() + "'."));
        }

        return null;
    }
}
