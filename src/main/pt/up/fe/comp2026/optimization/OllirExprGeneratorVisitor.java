package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;

import java.util.Objects;
import java.util.stream.Collectors;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;
    private final TypeUtils types;
    private final OptUtils ollirTypes;

    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils ollirTypes) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = ollirTypes;
    }

    @Override
    protected void buildVisitor() {
        // Basic Expressions
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(THIS, this::visitThis);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(PARENTHESES_EXPR, this::visitParentheses);

        // Operators
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(NEGATION_EXPR, this::visitNegation);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);

        // Array Operations
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(FIELD_ACCESS, this::visitLengths);

        // Object/Method Operations
        addVisit(NEW_OBJECT, this::visitNewObject);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(IMPLICIT_CALL, this::visitImplicitCall);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.intType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var boolType = TypeUtils.booleanType();
        String ollirBoolType = ollirTypes.toOllirType(boolType);
        String value = "true".equals(node.get("value")) ? "1" : "0";
        return new OllirExprResult(value + ollirBoolType);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var op = node.get("op");
        if ("&&".equals(op) || "||".equals(op)) {
            return visitShortCircuitLogicalExpr(node, op);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        JmmType resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        computation.append(op).append(resOllirType).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitShortCircuitLogicalExpr(JmmNode node, String op) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));
        var boolType = ollirTypes.toOllirType(TypeUtils.booleanType());
        var code = ollirTypes.nextTemp() + boolType;

        var rhsLabel = ollirTypes.nextTemp("logic_rhs");
        var trueLabel = ollirTypes.nextTemp("logic_true");
        var endLabel = ollirTypes.nextTemp("logic_end");

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());

        if ("&&".equals(op)) {
            computation.append(code).append(SPACE).append(ASSIGN).append(boolType).append(SPACE).append("0").append(boolType).append(END_STMT);
            computation.append("if(").append(lhs.getCode()).append(") goto ").append(rhsLabel).append(END_STMT);
            computation.append("goto ").append(endLabel).append(END_STMT);
            computation.append(rhsLabel).append(":\n");
            computation.append(rhs.getComputation());
            computation.append("if(").append(rhs.getCode()).append(") goto ").append(trueLabel).append(END_STMT);
            computation.append("goto ").append(endLabel).append(END_STMT);
            computation.append(trueLabel).append(":\n");
            computation.append(code).append(SPACE).append(ASSIGN).append(boolType).append(SPACE).append("1").append(boolType).append(END_STMT);
            computation.append(endLabel).append(":\n");
            return new OllirExprResult(code, computation);
        }

        computation.append(code).append(SPACE).append(ASSIGN).append(boolType).append(SPACE).append("1").append(boolType).append(END_STMT);
        computation.append("if(").append(lhs.getCode()).append(") goto ").append(endLabel).append(END_STMT);
        computation.append("goto ").append(rhsLabel).append(END_STMT);
        computation.append(rhsLabel).append(":\n");
        computation.append(rhs.getComputation());
        computation.append("if(").append(rhs.getCode()).append(") goto ").append(endLabel).append(END_STMT);
        computation.append(code).append(SPACE).append(ASSIGN).append(boolType).append(SPACE).append("0").append(boolType).append(END_STMT);
        computation.append(endLabel).append(":\n");

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var op = node.get("op");
        var valueNode = node.getChild(0);
        var value = visit(valueNode);
        var intType = ollirTypes.toOllirType(TypeUtils.intType());

        if ("+".equals(op)) return value;

        StringBuilder computation = new StringBuilder();
        computation.append(value.getComputation());

        if ("-".equals(op)) {
            var code = ollirTypes.nextTemp() + intType;
            computation.append(code).append(SPACE).append(ASSIGN).append(intType).append(SPACE).append("0").append(intType).append(SPACE)
                    .append("-").append(intType).append(SPACE).append(value.getCode()).append(END_STMT);
            return new OllirExprResult(code, computation);
        }

        if ("++".equals(op) || "--".equals(op)) {
            var code = ollirTypes.nextTemp() + intType;
            var numericOp = "++".equals(op) ? "+" : "-";
            computation.append(code).append(SPACE).append(ASSIGN).append(intType).append(SPACE).append(value.getCode())
                    .append(SPACE).append(numericOp).append(intType).append(SPACE).append("1").append(intType).append(END_STMT);

            if (valueNode.isInstance(VAR_REF_EXPR)) {
                computation.append(value.getCode()).append(SPACE).append(ASSIGN).append(intType).append(SPACE).append(code).append(END_STMT);
            }
            return new OllirExprResult(code, computation);
        }
        throw new RuntimeException("Unsupported unary operator '" + op + "'");
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = ollirTypes.sanitizeId(node.get("name"));
        JmmType type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        if (isFieldReference(node.get("name"), node)) {
            String code = ollirTypes.nextTemp() + ollirType;
            StringBuilder computation = new StringBuilder();
            computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                    .append("getfield(this, ").append(id).append(ollirType).append(")").append(ollirType).append(END_STMT);
            return new OllirExprResult(code, computation);
        }

        return new OllirExprResult(id + ollirType);
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        JmmType type = types.getExprType(node);
        return new OllirExprResult("this" + ollirTypes.toOllirType(type));
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        var childNode = node.getChild(0);
        var receiver = visit(childNode);
        var argResults = node.getChildren().stream().skip(1).map(this::visit).toList();

        var receiverType = types.getExprType(childNode);
        var returnType = types.getExprType(node);
        var ollirReturnType = ollirTypes.toOllirType(returnType);
        var methodName = node.get("name");

        StringBuilder computation = new StringBuilder();
        computation.append(receiver.getComputation());
        argResults.forEach(arg -> computation.append(arg.getComputation()));

        var invokeKind = "invokevirtual";
        var receiverCode = receiver.getCode();

        if (receiverType instanceof JmmClassType classType && classType.staticRef()) {
            invokeKind = "invokestatic";
            receiverCode = ollirTypes.sanitizeId(types.simpleName(classType.fullyQualifiedName()));
        }

        var argsCode = argResults.stream().map(OllirExprResult::getCode).collect(Collectors.joining(", "));
        var callCode = new StringBuilder().append(invokeKind).append("(").append(receiverCode).append(", \"").append(methodName).append("\"");

        if (!argsCode.isEmpty()) callCode.append(", ").append(argsCode);
        callCode.append(")").append(ollirReturnType);

        if (".V".equals(ollirReturnType)) {
            computation.append(callCode).append(END_STMT);
            return new OllirExprResult("", computation);
        }

        var code = ollirTypes.nextTemp() + ollirReturnType;
        computation.append(code).append(SPACE).append(ASSIGN).append(ollirReturnType).append(SPACE).append(callCode).append(END_STMT);
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        var sizeOllir = visit(node.getChild(0));
        var arrayTypeOllir = ollirTypes.toOllirType(types.getExprType(node));
        String arrayTemp = ollirTypes.nextTemp() + arrayTypeOllir;

        StringBuilder computation = new StringBuilder();
        computation.append(sizeOllir.getComputation());
        computation.append(arrayTemp).append(SPACE).append(ASSIGN).append(arrayTypeOllir).append(SPACE)
                .append("new(array, ").append(sizeOllir.getCode()).append(")").append(arrayTypeOllir).append(END_STMT);

        return new OllirExprResult(arrayTemp, computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        var arrayOllir = visit(node.getChild(0));
        var idxOllir = visit(node.getChild(1));
        var elementTypeOllir = ollirTypes.toOllirType(types.getExprType(node));
        String resultTemp = ollirTypes.nextTemp() + elementTypeOllir;

        StringBuilder computation = new StringBuilder();
        computation.append(arrayOllir.getComputation());
        computation.append(idxOllir.getComputation());
        computation.append(resultTemp).append(SPACE).append(ASSIGN).append(elementTypeOllir).append(SPACE)
                .append(arrayOllir.getCode()).append("[").append(idxOllir.getCode()).append("]").append(elementTypeOllir).append(END_STMT);

        return new OllirExprResult(resultTemp, computation);
    }

    private OllirExprResult visitParentheses(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitLengths(JmmNode node, Void unused) {
        var arrayExpr = node.getChild(0);
        var arrayOllir = visit(arrayExpr);
        if ("length".equals(node.get("name")) && types.getExprType(arrayExpr).isArray()) {
            String resultTemp = ollirTypes.nextTemp() + ".i32";
            StringBuilder computation = new StringBuilder();
            computation.append(arrayOllir.getComputation());
            computation.append(resultTemp).append(SPACE).append(ASSIGN).append(".i32").append(SPACE)
                    .append("arraylength(").append(arrayOllir.getCode()).append(").i32").append(END_STMT);
            return new OllirExprResult(resultTemp, computation);
        }
        return null;
    }

    private OllirExprResult visitNegation(JmmNode node, Void unused) {
        var childOllir = visit(node.getChild(0));
        String resultTemp = ollirTypes.nextTemp() + ".bool";
        StringBuilder computation = new StringBuilder();
        computation.append(childOllir.getComputation());
        computation.append(resultTemp).append(SPACE).append(ASSIGN).append(".bool").append(SPACE)
                .append("!.bool").append(SPACE).append(childOllir.getCode()).append(END_STMT);
        return new OllirExprResult(resultTemp, computation);
    }

    private OllirExprResult visitImplicitCall(JmmNode node, Void unused) {
        var argResults = node.getChildren().stream().map(this::visit).toList();
        var ollirReturnType = ollirTypes.toOllirType(types.getExprType(node));

        StringBuilder computation = new StringBuilder();
        argResults.forEach(arg -> computation.append(arg.getComputation()));

        var argsCode = argResults.stream().map(OllirExprResult::getCode).collect(Collectors.joining(", "));
        var callCode = new StringBuilder().append("invokevirtual(this, \"").append(node.get("name")).append("\"");

        if (!argsCode.isEmpty()) callCode.append(", ").append(argsCode);
        callCode.append(")").append(ollirReturnType);

        if (".V".equals(ollirReturnType)) {
            computation.append(callCode).append(END_STMT);
            return new OllirExprResult("", computation);
        }

        var code = ollirTypes.nextTemp() + ollirReturnType;
        computation.append(code).append(SPACE).append(ASSIGN).append(ollirReturnType).append(SPACE).append(callCode).append(END_STMT);
        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        var className = node.get("name");
        var temp = ollirTypes.nextTemp();
        var ollirType = "." + className;
        var computation = new StringBuilder();
        computation.append(temp).append(ollirType).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(").append(className).append(")").append(ollirType).append(END_STMT)
                .append("invokespecial(").append(temp).append(ollirType).append(", \"<init>\").V").append(END_STMT);
        return new OllirExprResult(temp + ollirType, computation);
    }

    private boolean isFieldReference(String varName, JmmNode scopeNode) {
        var methodDecl = scopeNode.getAncestor(METHOD_DECL);
        if (methodDecl.isPresent()) {
            var signature = types.getMethodDeclSignature(methodDecl.get());
            var method = table.getMethod(signature);
            if (method.isPresent()) {
                if (method.get().getLocalVariable(varName).isPresent() || method.get().getParameter(varName).isPresent()) {
                    return false;
                }
            }
        }
        return table.getField(varName).isPresent();
    }
}