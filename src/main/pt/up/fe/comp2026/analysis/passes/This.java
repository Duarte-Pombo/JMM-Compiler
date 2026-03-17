package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class This extends AnalysisVisitor {
    private JmmNode currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.THIS, this::visitThis);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable symbolTable) {
        currentMethod = method;
        return null;
    }

    private Void visitThis(JmmNode thisNode, SymbolTable symbolTable) {
        if (currentMethod == null) {
            return null;
        }

        var isStaticMethod = currentMethod.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
        if (!isStaticMethod) {
            return null;
        }

        addReport(Report.newError(Stage.SEMANTIC,
                NodeUtils.getLine(thisNode),
                NodeUtils.getColumn(thisNode),
                "'this' cannot be used in a static function",
                null));

        return null;
    }
}
