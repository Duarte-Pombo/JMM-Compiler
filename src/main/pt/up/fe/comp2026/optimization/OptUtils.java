package pt.up.fe.comp2026.optimization;

import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.LiteralElement;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;

import java.util.Set;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.TYPE;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {

    private static final Set<String> OLLIR_KEYWORDS = Set.of("bool", "i32", "V", "array", "String", "final", "goto", "if", "import", "interface", "new", "package"
            , "private", "protected", "public", "ret", "static", "this", "ldc", "invokespecial", "invokevirtual", "invokestatic", "arraylength", "getfield", "putfield", "getstatic", "putstatic",
            ".method", ".construct", ".field", "extends"
    );


    private final AccumulatorMap<String> temporaries;

    private final TypeUtils types;

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
    }


    public String nextTemp() {
        return nextTemp("tmp");
    }


    public String nextTemp(String prefix) {

        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;

        return prefix + nextTempNum;
    }


    public String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        return toOllirType(types.convertType(typeNode));
    }

    public String toOllirType(JmmType type) {
        if (type instanceof JmmArrayType arrayType) {
            return ".array".repeat(arrayType.dimension()) + toOllirType(arrayType.itemType());
        }

        if (type instanceof JmmPrimitiveType primitiveType) {
            return "." + toPrimitiveOllirType(primitiveType);
        }

        if (type instanceof JmmClassType classType) {
            return "." + simpleClassName(classType.fullyQualifiedName());
        }

        throw new IllegalArgumentException("Unsupported JMM type for OLLIR conversion: " + type);
    }

    private String toPrimitiveOllirType(JmmPrimitiveType type) {
        return switch (type) {
            case INT -> "i32";
            case BOOLEAN -> "bool";
            case VOID -> "V";
            default -> throw new IllegalArgumentException("Unsupported primitive type for OLLIR conversion: " + type);
        };
    }

    private String simpleClassName(String className) {
        var lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    public String sanitizeId(String id) {
        // If id is an OLLIR keyword, return a string literal with the id
        if (OLLIR_KEYWORDS.contains(id)) {
            return '"' + id + '"';
        }

        // If id starts with $, escape it
        if (id.startsWith("$")) {
            return '"' + id + '"';
        }

        return id;
    }


}
