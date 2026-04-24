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
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(NEW_ARRAY, this::visitNewArray);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
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

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = ollirTypes.sanitizeId(node.get("name"));
        JmmType type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);


        String code = id + ollirType;

        return new OllirExprResult(code);

    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        var size = node.getChild(0);
        var sizeOllir = visit(size);

        StringBuilder computation = new StringBuilder();
        computation.append(sizeOllir.getComputation());

        var arrayType = types.getExprType(node);
        String arrayTypeOllir = ollirTypes.toOllirType(arrayType);

        String arrayTemp = ollirTypes.nextTemp() + arrayTypeOllir;

        // tmp1.array.i32 :=.array.i32 new(array, tmp2.i32).array.i32;
        computation.append(arrayTemp).append(" :=").append(arrayTypeOllir).append(" ")
                .append("new(array, ").append(sizeOllir.getCode()).append(")")
                .append(arrayTypeOllir).append(";\n");
        return new OllirExprResult(arrayTemp, computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        var array = node.getChild(0);
        var idx = node.getChild(1);

        var arrayOllir = visit(array);
        var idxOllir = visit(idx);

        var elementType = types.getExprType(node);
        String elementTypeOllir = ollirTypes.toOllirType(elementType);

        String resultTemp = ollirTypes.nextTemp() + elementTypeOllir;

        StringBuilder computation = new StringBuilder();

        computation.append(arrayOllir.getComputation());
        computation.append(idxOllir.getComputation());

        // destination_temp.type :=.type array_reference[index_code].type;
        computation.append(resultTemp).append(" :=").append(elementTypeOllir).append(" ")
                .append(arrayOllir.getCode()).append("[").append(idxOllir.getCode()).append("]")
                .append(elementTypeOllir).append(";\n");

        return new OllirExprResult(resultTemp, computation);
    }
}
