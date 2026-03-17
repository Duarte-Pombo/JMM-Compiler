package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.ast.TypeUtils;

public class Conditions extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.IF_ELSE_STMT, this::visitCondition);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        MethodSymbol currentMethod = table.getMethod(signature)
                .orElseThrow(() -> new RuntimeException("Could not resolve method for signature " + signature));
        return null;
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