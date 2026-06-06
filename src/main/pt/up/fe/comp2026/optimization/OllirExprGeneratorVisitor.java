package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;

import java.util.List;
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
        addVisit(FIELD_ACCESS, this::visitFieldAccess);

        addVisit(NEW_ARRAY_BY_EXTENSION, this::visitNewArrayByExtension);
        addVisit(ARRAY_ELEM, this::visitArrayElem);

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
        var intType = ollirTypes.toOllirType(TypeUtils.intType());

        if ("++".equals(op) || "--".equals(op)) {
            return visitIncrementExpr(op, valueNode, intType);
        }

        var value = visit(valueNode);

        if ("+".equals(op)) return value;

        StringBuilder computation = new StringBuilder();
        computation.append(value.getComputation());

        if ("-".equals(op)) {
            var code = ollirTypes.nextTemp() + intType;
            computation.append(code).append(SPACE).append(ASSIGN).append(intType).append(SPACE).append("0").append(intType).append(SPACE)
                    .append("-").append(intType).append(SPACE).append(value.getCode()).append(END_STMT);
            return new OllirExprResult(code, computation);
        }

        throw new RuntimeException("Unsupported unary operator '" + op + "'");
    }

    private OllirExprResult visitIncrementExpr(String op, JmmNode valueNode, String intType) {
        var numericOp = "++".equals(op) ? "+" : "-";

        if (valueNode.isInstance(ARRAY_ACCESS)) {
            var array = visit(valueNode.getChild(0));
            var index = visit(valueNode.getChild(1));
            var oldValue = ollirTypes.nextTemp() + intType;
            var newValue = ollirTypes.nextTemp() + intType;

            StringBuilder computation = new StringBuilder();
            computation.append(array.getComputation());
            computation.append(index.getComputation());
            computation.append(oldValue).append(SPACE).append(ASSIGN).append(intType).append(SPACE)
                    .append(array.getCode()).append("[").append(index.getCode()).append("]").append(intType).append(END_STMT);
            appendIncrementComputation(computation, newValue, oldValue, numericOp, intType);
            computation.append(array.getCode()).append("[").append(index.getCode()).append("]").append(intType)
                    .append(SPACE).append(ASSIGN).append(intType).append(SPACE).append(newValue).append(END_STMT);

            return new OllirExprResult(newValue, computation);
        }

        if (valueNode.isInstance(FIELD_ACCESS)) {
            var receiver = visit(valueNode.getChild(0));
            var fieldType = ollirTypes.toOllirType(types.getExprType(valueNode));
            var field = ollirTypes.sanitizeId(valueNode.get("name")) + fieldType;
            var oldValue = ollirTypes.nextTemp() + intType;
            var newValue = ollirTypes.nextTemp() + intType;

            StringBuilder computation = new StringBuilder();
            computation.append(receiver.getComputation());
            computation.append(oldValue).append(SPACE).append(ASSIGN).append(intType).append(SPACE)
                    .append("getfield(").append(receiver.getCode()).append(", ").append(field).append(")").append(fieldType)
                    .append(END_STMT);
            appendIncrementComputation(computation, newValue, oldValue, numericOp, intType);
            computation.append("putfield(").append(receiver.getCode()).append(", ").append(field).append(", ")
                    .append(newValue).append(").V").append(END_STMT);

            return new OllirExprResult(newValue, computation);
        }

        var value = visit(valueNode);
        var newValue = ollirTypes.nextTemp() + intType;
        StringBuilder computation = new StringBuilder();
        computation.append(value.getComputation());
        appendIncrementComputation(computation, newValue, value.getCode(), numericOp, intType);

        if (valueNode.isInstance(VAR_REF_EXPR)) {
            var varName = valueNode.get("name");
            if (isFieldReference(varName, valueNode)) {
                computation.append("putfield(this, ")
                        .append(ollirTypes.sanitizeId(varName)).append(intType)
                        .append(", ").append(newValue)
                        .append(").V").append(END_STMT);
            } else {
                computation.append(value.getCode()).append(SPACE).append(ASSIGN).append(intType).append(SPACE)
                        .append(newValue).append(END_STMT);
            }
        }

        return new OllirExprResult(newValue, computation);
    }

    private void appendIncrementComputation(StringBuilder computation, String target, String value, String numericOp, String intType) {
        computation.append(target).append(SPACE).append(ASSIGN).append(intType).append(SPACE).append(value)
                .append(SPACE).append(numericOp).append(intType).append(SPACE).append("1").append(intType).append(END_STMT);
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
        var argNodes = node.getChildren().stream().skip(1).toList();
        var argResults = argNodes.stream().map(this::visit).toList();

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

        var paramTypes = types.getMethodCallParameterTypes(node).orElse(List.of());
        var argsCode = buildArgsCode(argNodes, argResults, paramTypes, computation);
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
        var arrayTypeOllir = ollirTypes.toOllirType(types.getExprType(node));
        String arrayTemp = ollirTypes.nextTemp() + arrayTypeOllir;

        StringBuilder computation = new StringBuilder();
        StringBuilder sizesCode = new StringBuilder();

        // Iterate over all children (each represents the size of a dimension)
        for (int i = 0; i < node.getNumChildren(); i++) {
            var sizeOllir = visit(node.getChild(i));
            computation.append(sizeOllir.getComputation());

            if (i > 0) {
                sizesCode.append(", ");
            }
            sizesCode.append(sizeOllir.getCode());
        }

        computation.append(arrayTemp).append(SPACE).append(ASSIGN).append(arrayTypeOllir).append(SPACE)
                .append("new(array, ").append(sizesCode).append(")").append(arrayTypeOllir).append(END_STMT);

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

    private OllirExprResult visitNewArrayByExtension(JmmNode node, Void unused) {
        var arrayInitNode = node.getChild(0);
        return generateArrayInitializer(arrayInitNode, types.getExprType(node));
    }

    private OllirExprResult generateArrayInitializer(JmmNode arrayInitNode, JmmType arrayType) {
        var elements = arrayInitNode.getChildren();
        int size = elements.size();

        var arrayTypeOllir = ollirTypes.toOllirType(arrayType);
        var elementType = peelArrayDimension((JmmArrayType) arrayType);
        var elementTypeOllir = ollirTypes.toOllirType(elementType);

        String arrayTemp = ollirTypes.nextTemp() + arrayTypeOllir;
        StringBuilder computation = new StringBuilder();

        String sizeCode = size + ".i32";
        computation.append(arrayTemp).append(SPACE).append(ASSIGN).append(arrayTypeOllir).append(SPACE)
                .append("new(array, ").append(sizeCode).append(")").append(arrayTypeOllir).append(END_STMT);

        for (int i = 0; i < size; i++) {
            var elemNode = elements.get(i).getChild(0);
            var elemOllir = elemNode.isInstance(ARRAY_INIT)
                    ? generateArrayInitializer(elemNode, elementType)
                    : visit(elemNode);

            computation.append(elemOllir.getComputation());
            computation.append(arrayTemp).append("[").append(i).append(".i32]").append(elementTypeOllir)
                    .append(SPACE).append(ASSIGN).append(elementTypeOllir).append(SPACE)
                    .append(elemOllir.getCode()).append(END_STMT);
        }

        return new OllirExprResult(arrayTemp, computation);
    }

    private JmmType peelArrayDimension(JmmArrayType arrayType) {
        int dims = arrayType.dimension();
        return dims > 1
                ? new JmmArrayType(arrayType.itemType(), dims - 1)
                : arrayType.itemType();
    }

    private OllirExprResult visitArrayElem(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitParentheses(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitFieldAccess(JmmNode node, Void unused) {
        var recvNode = node.getChild(0);
        var recvOllir = visit(recvNode);
        var fieldName = ollirTypes.sanitizeId(node.get("name"));
        var fieldType = ollirTypes.toOllirType(types.getExprType(node));

        if ("length".equals(node.get("name")) && types.getExprType(recvNode).isArray()) {
            String resultTemp = ollirTypes.nextTemp() + ".i32";
            StringBuilder computation = new StringBuilder();
            computation.append(recvOllir.getComputation());
            computation.append(resultTemp).append(SPACE).append(ASSIGN).append(".i32").append(SPACE)
                    .append("arraylength(").append(recvOllir.getCode()).append(").i32").append(END_STMT);
            return new OllirExprResult(resultTemp, computation);
        }

        var resultTemp = ollirTypes.nextTemp() + fieldType;
        StringBuilder computation = new StringBuilder();
        computation.append(recvOllir.getComputation());
        computation.append(resultTemp).append(SPACE).append(ASSIGN).append(fieldType).append(SPACE)
                .append("getfield(").append(recvOllir.getCode()).append(", ")
                .append(fieldName).append(fieldType)
                .append(")").append(fieldType).append(END_STMT);

        return new OllirExprResult(resultTemp, computation);
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
        var argNodes = node.getChildren();
        var argResults = argNodes.stream().map(this::visit).toList();
        var ollirReturnType = ollirTypes.toOllirType(types.getExprType(node));

        StringBuilder computation = new StringBuilder();
        argResults.forEach(arg -> computation.append(arg.getComputation()));

        var paramTypes = types.getImplicitCallParameterTypes(node).orElse(List.of());
        var argsCode = buildArgsCode(argNodes, argResults, paramTypes, computation);
        var className = ollirTypes.sanitizeId(table.getClassName());

        var callCode = new StringBuilder()
                .append("invokevirtual(this.").append(className)
                .append(", \"").append(node.get("name")).append("\"");

        if (!argsCode.isEmpty()) {
            callCode.append(", ").append(argsCode);
        }
        callCode.append(")").append(ollirReturnType);

        if (".V".equals(ollirReturnType)) {
            computation.append(callCode).append(END_STMT);
            return new OllirExprResult("", computation);
        }

        var code = ollirTypes.nextTemp() + ollirReturnType;
        computation.append(code).append(SPACE)
                   .append(ASSIGN).append(ollirReturnType).append(SPACE)
                   .append(callCode).append(END_STMT);
                   
        return new OllirExprResult(code, computation);
    }

    private String buildArgsCode(List<JmmNode> argNodes, List<OllirExprResult> argResults,
                                 List<JmmType> paramTypes, StringBuilder computation) {
        var args = new StringBuilder();

        for (int i = 0; i < argResults.size(); i++) {
            if (i > 0) {
                args.append(", ");
            }

            var targetType = i < paramTypes.size() ? paramTypes.get(i) : null;
            args.append(adaptArgCode(argNodes.get(i), argResults.get(i), targetType, computation));
        }

        return args.toString();
    }

    private String adaptArgCode(JmmNode argNode, OllirExprResult argResult, JmmType targetType,
                                StringBuilder computation) {
        if (targetType == null) {
            return argResult.getCode();
        }

        var sourceType = types.getExprType(argNode);
        if (sourceType.equals(targetType) || !types.isAssignable(sourceType, targetType)) {
            return argResult.getCode();
        }

        if (!sourceType.isClass() || !targetType.isClass()) {
            return argResult.getCode();
        }

        var targetOllirType = ollirTypes.toOllirType(targetType);
        if (argNode.isInstance(THIS)) {
            return "this" + targetOllirType;
        }

        var code = ollirTypes.nextTemp() + targetOllirType;
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(targetOllirType).append(SPACE)
                .append(argResult.getCode()).append(END_STMT);

        return code;
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        var className = node.get("name");
        var temp = ollirTypes.nextTemp();
        var ollirType = "." + className;
        var computation = new StringBuilder();

        // visit all arguments passed to the constructor
        var argResults = node.getChildren().stream().map(this::visit).toList();
        
        // append the computations for the arguments FIRST
        argResults.forEach(arg -> computation.append(arg.getComputation()));
        
        // extract the variables/temps representing the argument values
        var argsCode = argResults.stream().map(OllirExprResult::getCode).collect(Collectors.joining(", "));

        // instantiate the object via 'new'
        computation.append(temp).append(ollirType).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(").append(className).append(")").append(ollirType).append(END_STMT);

        // call the constructor (<init>) with the evaluated arguments
        computation.append("invokespecial(").append(temp).append(ollirType).append(", \"<init>\"");
        if (!argsCode.isEmpty()) {
            computation.append(", ").append(argsCode);
        }
        computation.append(").V").append(END_STMT);
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
