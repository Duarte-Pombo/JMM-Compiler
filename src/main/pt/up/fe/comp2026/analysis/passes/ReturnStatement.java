package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class ReturnStatement extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.MAIN_METHOD_DECL, this::visitMainMethodDecl);
        addVisit(JmmKind.GENERAL_METHOD_DECL, this::visitMethodDecl);
    }

    private Void visitMainMethodDecl(JmmNode method, SymbolTable symbolTable) {
        var returnStmts = method.getDescendants(JmmKind.RETURN_STMT);

        for (var returnStmt : returnStmts) {
            if (returnStmt.getNumChildren() > 0) {
                addReport(newError(returnStmt,
                        "Method 'main' cannot return a value."));
            }
        }
        
        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var types = TypeUtils.with(table);
        var signature = types.getMethodDeclSignature(method);
        var methodOpt = table.getMethod(signature);

        if (methodOpt.isEmpty()) {
            return null;
        }

        var declaredReturnType = methodOpt.get().returnType();

        if (declaredReturnType.equals(JmmPrimitiveType.VOID)) {
            for (var returnStmt : method.getDescendants(JmmKind.RETURN_STMT)) {
                if (returnStmt.getNumChildren() > 0) {
                    addReport(newError(returnStmt,
                            "Void method '" + signature + "' cannot return a value."));
                }
            }
            return null;
        }

        var returnStmts = method.getDescendants(JmmKind.RETURN_STMT);

        if (returnStmts.isEmpty()) {
            addReport(newError(method,
                    "Method '" + signature + "' is missing a return statement."));
            return null;
        }

        for (var returnStmt : returnStmts) {
            if (returnStmt.getNumChildren() == 0) {
                addReport(newError(returnStmt,
                        "Method '" + signature + "' must return a value of type '"
                                + declaredReturnType.print() + "'."));
                continue;
            }

            var returnExpr = returnStmt.getChild(0);
            var returnType = types.getExprType(returnExpr);

            if (!types.isAssignable(returnType, declaredReturnType)) {
                addReport(newError(returnStmt,
                        "Return type mismatch in method '" + signature
                                + "': expected '" + declaredReturnType.print()
                                + "' but got '" + returnType.print() + "'."));
            }
        }

        return null;
    }
}
