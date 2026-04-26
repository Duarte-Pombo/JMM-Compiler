package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;

import java.util.Objects;
import java.util.stream.Collectors;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private final SymbolTable table;
    private final TypeUtils types;
    private final OptUtils ollirTypes;
    private final OllirExprGeneratorVisitor exprVisitor;
    private MethodSymbol currentMethod;
    private JmmNode currentClass;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        this.exprVisitor = new OllirExprGeneratorVisitor(table, ollirTypes);
        this.currentMethod = null;
    }

    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(PACKAGE_DECL, this::visitPackageDecl);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(IMPORT_DECLARATION, this::visitImportDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::simpleVarDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(COMPOUND_STMT, this::visitCompoundStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssign);
    }

    private String simpleVarDecl(JmmNode varDecl, Void unused) {
        return varDecl.get("name") + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class)) + ";";
    }

    private String visitParam(JmmNode varDecl, Void unused) {
        return varDecl.get("name") + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class));
    }

    private String visitPackageDecl(JmmNode packageDecl, Void unused) {
        return "package " + String.join(".", packageDecl.getObjectAsList("path", String.class)) + ";\n";
    }

    private String visitImportDecl(JmmNode importDecl, Void unused) {
        return "import " + String.join(".", importDecl.getObjectAsList("path", String.class)) + ";\n";
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhsNode = node.getChild(0);
        var rhsNode = node.getChild(1);
        var rhs = exprVisitor.visit(rhsNode);

        JmmType lhsType = types.getExprType(lhsNode);
        String typeString = ollirTypes.toOllirType(lhsType);
        var code = new StringBuilder();

        if (lhsNode.isInstance(VAR_REF_EXPR)) {
            var varName = lhsNode.get("name");
            var sanitizedName = ollirTypes.sanitizeId(varName);

            code.append(rhs.getComputation());

            if (isFieldReference(varName, lhsNode)) {
                code.append("putfield(this, ")
                        .append(sanitizedName).append(typeString)
                        .append(", ")
                        .append(rhs.getCode())
                        .append(").V")
                        .append(END_STMT);
                return code.toString();
            }

            code.append(sanitizedName)
                    .append(typeString)
                    .append(SPACE)
                    .append(ASSIGN).append(typeString).append(SPACE)
                    .append(rhs.getCode())
                    .append(END_STMT);

            return code.toString();
        }

        if (lhsNode.isInstance(ARRAY_ACCESS)) {
            var arrayExpr = exprVisitor.visit(lhsNode.getChild(0));
            var indexExpr = exprVisitor.visit(lhsNode.getChild(1));

            code.append(arrayExpr.getComputation());
            code.append(indexExpr.getComputation());
            code.append(rhs.getComputation());

            code.append(arrayExpr.getCode())
                    .append("[")
                    .append(indexExpr.getCode())
                    .append("]")
                    .append(typeString)
                    .append(SPACE)
                    .append(ASSIGN).append(typeString).append(SPACE)
                    .append(rhs.getCode())
                    .append(END_STMT);

            return code.toString();
        }

        if (lhsNode.isInstance(FIELD_ACCESS)) {
            var recvExpr = exprVisitor.visit(lhsNode.getChild(0));
            var fieldName = ollirTypes.sanitizeId(lhsNode.get("name"));

            code.append(recvExpr.getComputation());
            code.append(rhs.getComputation());
            code.append("putfield(")
                    .append(recvExpr.getCode())
                    .append(", ")
                    .append(fieldName)
                    .append(typeString)
                    .append(", ")
                    .append(rhs.getCode())
                    .append(").V")
                    .append(END_STMT);

            return code.toString();
        }

        var lhs = exprVisitor.visit(lhsNode);
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());
        code.append(lhs.getCode())
                .append(SPACE)
                .append(ASSIGN).append(typeString).append(SPACE)
                .append(rhs.getCode())
                .append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var expr = exprVisitor.visit(node.getChild(0));
        return expr.getComputation();
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

    private String visitReturn(JmmNode node, Void unused) {
        if (currentMethod == null) {
            throw new IllegalStateException("Return statement found outside of a method declaration");
        }
        JmmType retType = currentMethod.returnType();
        String ollirRetType = ollirTypes.toOllirType(retType);
        StringBuilder code = new StringBuilder();

        if (node.getNumChildren() == 0) {
            code.append("ret").append(ollirRetType).append(END_STMT);
            return code.toString();
        }

        var expr = exprVisitor.visit(node.getChild(0));
        code.append(expr.getComputation());
        code.append("ret").append(ollirRetType).append(SPACE).append(expr.getCode()).append(END_STMT);
        return code.toString();
    }

    private String nextLabel(String prefix) {
        return ollirTypes.nextTemp(prefix);
    }

    private String visitCompoundStmt(JmmNode node, Void unused) {
        return node.getChildren(STMT).stream().map(this::visit).collect(Collectors.joining());
    }

    private String visitWhileStmt(JmmNode whileStmt, Void unused) {
        var beginLabel = nextLabel("loop_begin");
        var bodyLabel = nextLabel("loop_body");
        var exitLabel = nextLabel("loop_exit");
        var condExpr = exprVisitor.visit(whileStmt.getChild(0));

        StringBuilder code = new StringBuilder();
        code.append(beginLabel).append(":\n");
        code.append(condExpr.getComputation());
        code.append("if(").append(condExpr.getCode()).append(") goto ").append(bodyLabel).append(END_STMT);
        code.append("goto ").append(exitLabel).append(END_STMT);
        code.append(bodyLabel).append(":\n");
        code.append(visit(whileStmt.getChild(1)));
        code.append("goto ").append(beginLabel).append(END_STMT);
        code.append(exitLabel).append(":\n");
        return code.toString();
    }

    private String visitIfElseStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        String thenBlock = visit(node.getChild(1));
        String elseBlock = visit(node.getChild(2));

        var trueLabel = nextLabel("if_true");
        var exitLabel = nextLabel("if_exit");
        var cond = exprVisitor.visit(node.getChild(0));

        code.append(cond.getComputation());
        code.append("if(").append(cond.getCode()).append(") goto ").append(trueLabel).append(END_STMT);
        code.append(elseBlock);
        code.append("goto ").append(exitLabel).append(END_STMT);
        code.append(trueLabel).append(":").append(NL);
        code.append(thenBlock);
        code.append(exitLabel).append(":").append(NL);
        return code.toString();
    }

    private String visitArrayAssign(JmmNode node, Void unused) {
        String array = node.get("var");
        var idx = node.getChild(0);
        var val = node.getChild(1);

        var idxOllir = exprVisitor.visit(idx);
        var valOllir = exprVisitor.visit(val);

        var arrayType = types.getVariableType(array, node)
                .orElseThrow(() -> new RuntimeException("Array variable '" + array + "' is not defined in current scope"));
        String arrayTypeOllir = ollirTypes.toOllirType(arrayType);

        var elementType = types.getExprType(val);
        String elementTypeOllir = ollirTypes.toOllirType(elementType);

        StringBuilder code = new StringBuilder();
        String arrayCode = ollirTypes.sanitizeId(array) + arrayTypeOllir;

        if (isFieldReference(array, node)) {
            arrayCode = ollirTypes.nextTemp() + arrayTypeOllir;
            code.append(arrayCode).append(SPACE).append(ASSIGN).append(arrayTypeOllir).append(SPACE)
                    .append("getfield(this, ")
                    .append(ollirTypes.sanitizeId(array)).append(arrayTypeOllir)
                    .append(")").append(arrayTypeOllir).append(END_STMT);
        }

        code.append(idxOllir.getComputation());
        code.append(valOllir.getComputation());

        code.append(arrayCode).append("[").append(idxOllir.getCode()).append("]")
                .append(elementTypeOllir).append(SPACE).append(ASSIGN)
                .append(elementTypeOllir).append(SPACE)
                .append(valOllir.getCode()).append(END_STMT);
        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        currentMethod = table.getMethod(TypeUtils.with(table).getMethodDeclSignature(node)).orElseThrow();
        StringBuilder code = new StringBuilder(".method ");

        String visibility = node.getOptional("visibility").orElse("");

        if (visibility.equals("public")) {
            code.append("public ");
        } else if (visibility.equals("private")) {
            code.append("private ");
        } else if (visibility.equals("protected")) {
            code.append("protected ");
        }

        if (node.getObject("isStatic", Boolean.class)) {
            code.append("static ");
        }

        var name = ollirTypes.sanitizeId(node.get("name"));
        code.append(name);

        var paramsCode = currentMethod.parameters().stream().map(param -> {
            var paramName = ollirTypes.sanitizeId(param.name());
            var paramType = ollirTypes.toOllirType(param.type());
            return paramName + paramType;
        }).collect(Collectors.joining(", "));
        code.append("(").append(paramsCode).append(")");

        var retType = ollirTypes.toOllirType(currentMethod.returnType());
        code.append(retType).append(L_BRACKET);

        var stmts = node.getChildren(STMT);
        if (!stmts.isEmpty()) {
            var stmtsCode = stmts.stream().map(this::visit).collect(Collectors.joining("\n   ", "   ", ""));
            code.append(stmtsCode);
        }

        if (Objects.equals(retType, ".V")) {
            if (stmts.isEmpty() || !Objects.equals(stmts.getLast().getKind().toString(), RETURN_STMT.toString())) {
                code.append("   ret.V;\n");
            }
        }
        code.append(R_BRACKET).append(NL);
        currentMethod = null;
        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {
        currentClass = node;
        StringBuilder code = new StringBuilder();
        code.append(NL).append(table.getClassName());
        node.getOptional("parent").ifPresent(parent -> code.append(" extends ").append(parent));
        code.append(L_BRACKET).append(NL);

        for (Symbol field : table.getFields()) {
            code.append(".field ").append(ollirTypes.sanitizeId(field.name()))
                    .append(ollirTypes.toOllirType(field.type())).append(END_STMT);
        }
        code.append(NL).append(buildConstructor()).append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            code.append(visit(child));
        }
        code.append(R_BRACKET);
        currentClass = null;
        return code.toString();
    }

    private String buildConstructor() {
        // Fix 1: invokespecial must annotate 'this' with the super class type
        String superName = (currentClass != null && currentClass.getOptional("parent").isPresent())
                ? currentClass.get("parent")
                : "Object";

        // Fix 2: emit putfield for every field that has an initializer expression
        StringBuilder fieldInits = new StringBuilder();
        if (currentClass != null) {
            for (var varDecl : currentClass.getChildren(VAR_DECL)) {
                // varDecl children: child(0) = type, child(1) = init expr (optional)
                if (varDecl.getNumChildren() < 2) continue;

                var fieldName = varDecl.get("name");
                var fieldOpt = table.getField(fieldName);
                if (fieldOpt.isEmpty()) continue;

                var typeOllir = ollirTypes.toOllirType(fieldOpt.get().type());
                var exprResult = exprVisitor.visit(varDecl.getChild(1));

                fieldInits.append(exprResult.getComputation());
                fieldInits.append("    putfield(this, ")
                        .append(ollirTypes.sanitizeId(fieldName)).append(typeOllir)
                        .append(", ").append(exprResult.getCode())
                        .append(").V").append(END_STMT);
            }
        }

        return """
                .construct %s().V {
                    invokespecial(this.%s, "<init>").V;
                %s}
                """.formatted(table.getClassName(), superName, fieldInits);
    }

    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        node.getChildren().stream().map(this::visit).forEach(code::append);
        return code.toString();
    }

    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return "";
    }
}
