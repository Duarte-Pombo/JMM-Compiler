package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class Conditions extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.IF_ELSE_STMT, this::visitCondition);
        addVisit(JmmKind.WHILE_STMT, this::visitCondition);
        addVisit(JmmKind.FOR_STMT,this::visitCondFor);
    }

    private Void visitCondition(JmmNode stmt, SymbolTable table) {
        validateBooleanCondition(stmt, stmt.getChild(0), table);
        return null;
    }

    private Void visitCondFor(JmmNode stmt, SymbolTable table) {
        var conditionWrapper = stmt.getChildren(JmmKind.FOR_CONDITION);

        if (conditionWrapper.isEmpty()) {
            return null;
        }

        var wrapperNode = conditionWrapper.getFirst();

        var conditionNode = wrapperNode.getChild(0);

        validateBooleanCondition(stmt, conditionNode, table);
        return null;
    }

    private void validateBooleanCondition(JmmNode reportNode, JmmNode conditionNode, SymbolTable table) {
        var conditionType = TypeUtils.with(table).getExprType(conditionNode);

        if (conditionType.equals(TypeUtils.booleanType()) && !conditionType.isArray()) {
            return;
        }

        addReport(newError(reportNode, "Condition expression must be of type boolean"));
    }
}