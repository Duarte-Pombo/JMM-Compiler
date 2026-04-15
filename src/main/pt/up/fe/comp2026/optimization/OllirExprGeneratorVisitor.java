package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.AccessType;
import pt.up.fe.comp2026.ast.TypeUtils;

import java.util.stream.Collectors;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
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
        this.ollirTypes = ollirTypes; // We need to pass ollirTypes, to ensure labels are unique
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(NEGATION_EXPR, this::visitNegationExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(PARENTHESES_EXPR, this::visitParenthesesExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
    }

    private OllirExprResult visitParenthesesExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
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

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        JmmType resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        JmmType type = types.getExprType(node);
        computation.append(op).append(ollirTypes.toOllirType(type)).append(SPACE)
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
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(boolType).append(SPACE)
                    .append("0").append(boolType).append(END_STMT);

            computation.append("if(").append(lhs.getCode()).append(") goto ").append(rhsLabel).append(END_STMT);
            computation.append("goto ").append(endLabel).append(END_STMT);
            computation.append(rhsLabel).append(":\n");
            computation.append(rhs.getComputation());
            computation.append("if(").append(rhs.getCode()).append(") goto ").append(trueLabel).append(END_STMT);
            computation.append("goto ").append(endLabel).append(END_STMT);
            computation.append(trueLabel).append(":\n");
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(boolType).append(SPACE)
                    .append("1").append(boolType).append(END_STMT);
            computation.append(endLabel).append(":\n");
            return new OllirExprResult(code, computation);
        }

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(boolType).append(SPACE)
                .append("1").append(boolType).append(END_STMT);

        computation.append("if(").append(lhs.getCode()).append(") goto ").append(endLabel).append(END_STMT);
        computation.append("goto ").append(rhsLabel).append(END_STMT);
        computation.append(rhsLabel).append(":\n");
        computation.append(rhs.getComputation());
        computation.append("if(").append(rhs.getCode()).append(") goto ").append(endLabel).append(END_STMT);
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(boolType).append(SPACE)
                .append("0").append(boolType).append(END_STMT);
        computation.append(endLabel).append(":\n");

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitNegationExpr(JmmNode node, Void unused) {
        var value = visit(node.getChild(0));
        var boolType = ollirTypes.toOllirType(TypeUtils.booleanType());
        var code = ollirTypes.nextTemp() + boolType;

        StringBuilder computation = new StringBuilder();
        computation.append(value.getComputation());
        computation.append(code).append(SPACE)
                .append(ASSIGN).append(boolType).append(SPACE)
                .append("!").append(boolType).append(SPACE)
                .append(value.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var op = node.get("op");
        var valueNode = node.getChild(0);
        var value = visit(valueNode);
        var intType = ollirTypes.toOllirType(TypeUtils.intType());

        if ("+".equals(op)) {
            return value;
        }

        StringBuilder computation = new StringBuilder();
        computation.append(value.getComputation());

        if ("-".equals(op)) {
            var code = ollirTypes.nextTemp() + intType;
            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(intType).append(SPACE)
                    .append("0").append(intType).append(SPACE)
                    .append("-").append(intType).append(SPACE)
                    .append(value.getCode()).append(END_STMT);

            return new OllirExprResult(code, computation);
        }

        if ("++".equals(op) || "--".equals(op)) {
            var code = ollirTypes.nextTemp() + intType;
            var numericOp = "++".equals(op) ? "+" : "-";

            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(intType).append(SPACE)
                    .append(value.getCode()).append(SPACE)
                    .append(numericOp).append(intType).append(SPACE)
                    .append("1").append(intType).append(END_STMT);

            if (valueNode.isInstance(VAR_REF_EXPR)) {
                computation.append(value.getCode()).append(SPACE)
                        .append(ASSIGN).append(intType).append(SPACE)
                        .append(code).append(END_STMT);
            }

            return new OllirExprResult(code, computation);
        }

        throw new RuntimeException("Unsupported unary operator '" + op + "'");
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = ollirTypes.sanitizeId(node.get("name"));
        JmmType type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);


        String code = id + ollirType;

        return new OllirExprResult(code);

    }


}
