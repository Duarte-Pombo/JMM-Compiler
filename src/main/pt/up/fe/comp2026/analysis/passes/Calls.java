package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class Calls extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_CALL, this::visitCall);
        addVisit(JmmKind.IMPLICIT_CALL, this::visitCall);
    }

    private Void visitCall(JmmNode callExpr, SymbolTable symbolTable) {
        var typeUtils = new TypeUtils(symbolTable);

        if (callExpr.isInstance(JmmKind.METHOD_CALL)) {
            var recvType = getReceiverType(callExpr, typeUtils);
            if (recvType == null) {
                return null;
            }

            if (!recvType.isClass()) {
                addReport(newError(callExpr, "Method call receiver is not a class type: " + recvType));
                return null;
            }
        } else if (callExpr.isInstance(JmmKind.IMPLICIT_CALL)) {
            var callerMethod = callExpr.getAncestor(JmmKind.METHOD_DECL);
            boolean isCallerStatic = false;

            if (callerMethod.isPresent()) {
                isCallerStatic = callerMethod.get().getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
            } else {
                var mainCaller = callExpr.getAncestor(JmmKind.MAIN_METHOD_DECL);
                if (mainCaller.isPresent()) {
                    isCallerStatic = true;
                    callerMethod = mainCaller; 
                }
            }

            if (isCallerStatic && callerMethod.isPresent()) {
                String targetMethodName = callExpr.get("name");
                if (isTargetMethodInstance(targetMethodName, callerMethod.get())) {
                    addReport(newError(callExpr, "Cannot call instance method '" + targetMethodName + "' from a static context."));
                    return null;
                }
            }
        }

        try {
            typeUtils.getExprType(callExpr);
        } catch (RuntimeException e) {
            addReport(newError(callExpr, "Method call failed: " + e.getMessage()));
        }

        return null;
    }

    private JmmType getReceiverType(JmmNode callExpr, TypeUtils typeUtils) {
        try {
            return typeUtils.getExprType(callExpr.getChild(0));
        } catch (RuntimeException e) {
            addReport(newError(callExpr, "Method call failed: " + e.getMessage()));
            return null;
        }
    }

    private boolean isTargetMethodInstance(String methodName, JmmNode callerMethodNode) {
        var classNode = callerMethodNode.getParent();
        if (classNode == null) {
            return false; 
        }

        for (var method : classNode.getChildren(JmmKind.METHOD_DECL)) {
            if (methodName.equals(method.get("name"))) {
                return !method.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
            }
        }

        return false;
    }
}
