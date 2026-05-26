package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class VoidDeclarations extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        // register the visitor for both variable and parameter declarations
        addVisit(JmmKind.VAR_DECL, this::visitDeclaration);
        addVisit(JmmKind.PARAM, this::visitDeclaration);
    }

    private Void visitDeclaration(JmmNode declNode, SymbolTable table) {
        var types = TypeUtils.with(table);

        // getDeclaredType extracts the type child from both VAR_DECL and PARAM
        var declType = types.getDeclaredType(declNode);

        // check if the type is void
        if (declType.print().contains("void")) {

            // error message
            String nodeType = declNode.isInstance(JmmKind.PARAM) ? "Parameters" : "Variables";
            
            addReport(newError(declNode, nodeType + " cannot be declared with type 'void'."));
        }

        return null;
    }
}
