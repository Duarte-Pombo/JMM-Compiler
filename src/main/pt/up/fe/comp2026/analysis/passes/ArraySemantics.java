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

        if (newArray.getChild(0).getChildren().isEmpty()) {
            var message = "The first dimension must be determined as an integer. It was left empty";
            addReport(newError(newArray.getChild(0), message));
        }

        for (JmmNode arrayDim : newArray.getChildren()) {
            var expr = arrayDim.getChild(0);
            var exprType = types.getExprType(expr);

            if (!types.isAssignable(exprType, expectedType)) {
                var message = "Array dimension size must be an integer. Got '" + exprType.print() + "'.";
                addReport(newError(expr, message));
            }
        }
        return null;
    }
}
