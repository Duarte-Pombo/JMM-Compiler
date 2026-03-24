package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
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

        var expectedType = TypeUtils.intType();

        var arrayInitNodes = newArrayByExtension.getChildren(JmmKind.ARRAY_INIT);
        if (arrayInitNodes.isEmpty()) {
            return null;
        }
        var arrayInit = arrayInitNodes.getFirst();

        for (JmmNode arrayElem : arrayInit.getChildren(JmmKind.ARRAY_ELEM)) {

            if (arrayElem.getChildren().isEmpty()) continue;

            JmmNode expr = arrayElem.getChild(0);
            var exprType = types.getExprType(expr);

            if (!types.isAssignable(exprType, expectedType)) {

                var message = "Type mismatch in array initializer. Expected '" + expectedType.print() +
                              "' but got '" + exprType.print() + "'.";

                addReport(newError(expr, message));
            }
        }

        return null;
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
        var expectedType = TypeUtils.intType();

        String arrayName = arrayAssignStmt.get("var");

        var arrayTypeOpt = types.getVariableType(arrayName, arrayAssignStmt);

        if (arrayTypeOpt.isEmpty() || !arrayTypeOpt.get().isArray()) {
            addReport(newError(arrayAssignStmt, "Variable '" + arrayName + "' is not an array."));
            return null;
        }

        int numChildren = arrayAssignStmt.getNumChildren();

        for (int i = 0; i < numChildren - 1; i++) {
            var indexExpr = arrayAssignStmt.getChild(i);
            var indexType = types.getExprType(indexExpr);

            if (!types.isAssignable(indexType, expectedType)) {
                addReport(newError(indexExpr, "Array access index must be an integer. Got '" + indexType.print() + "'."));
            }
        }

        var assignedValueExpr = arrayAssignStmt.getChild(numChildren - 1);
        var assignedValueType = types.getExprType(assignedValueExpr);

        if (!types.isAssignable(assignedValueType, expectedType)) {
            var message = "Cannot assign type '" + assignedValueType.print() + "' to array of base type 'int'.";
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
            addReport(newError(arrayAccess, "Variable '" + array + "' is not an array."));
            return null;
        }

        var idx_expr = arrayAccess.getChild(1);
        var idx = types.getExprType(idx_expr);

        if (!types.isAssignable(idx, expectedType)) {
            addReport(newError(arrayAccess, "Expr '" + idx_expr + "is not an integer."));
        }


        return null;
    }
}
