package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class Conditions extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.IF_ELSE_STMT, this::visitCondition);
        addVisit(JmmKind.WHILE_STMT, this::visitCondition);
    }

    private Void visitCondition(JmmNode stmt, SymbolTable table) {
        var conditionNode = stmt.getChild(0);
        var conditionType = TypeUtils.with(table).getExprType(conditionNode);

        if (conditionType.equals(TypeUtils.booleanType()) && !conditionType.isArray()) {
            return null;
        }

        var message ="Condition expression must be of type boolean";
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(stmt),
                NodeUtils.getColumn(stmt), message, null)
        );
        return null;
    }
}