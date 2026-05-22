package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    private final Map<String, String> fullClassnames;

    private final Importer importer;

    public JasminUtils(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        this.importer = Importer.fromThisClassPath();
        // Build imports table
        fullClassnames = new HashMap<>();

        // Predefined classnames
        var classUnit = ollirResult.getOllirClass();
        var classFqName = classUnit.getClassFullyQualifiedName();
        fullClassnames.put("this", classFqName);
        fullClassnames.put(classUnit.getClassName(), classFqName);
        // This will be get caught as STRING OLLIR element type.
        // And classes cannot be named String, since it is an OLLIR reserved keyword
        //imports.put("String", "java/lang/String");

        for (var fullImport : ollirResult.getOllirClass().getImports()) {
            var splitted = fullImport.split("\\.");

            // Last element will be the key
            var key = splitted[splitted.length - 1];
            fullClassnames.put(key, fullImport.replace('.', '/'));
        }
    }



    public String getTypePrefix(Type type) {
        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32, BOOLEAN -> "i";
                case VOID -> "";
                case STRING -> "a";
            };
        }

        if (type instanceof ArrayType || type instanceof ClassType) {
            return "a";
        }
        throw new RuntimeException("Not implemented for element type '" + type + "'");
    }

    public String getTypeDescriptor(Type type) {

        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case VOID -> "V";
                case STRING -> "L" + getClassPath("String") + ";";
                default ->
                        throw new RuntimeException("Not implemented for element type '" + builtinType.getKind() + "'");
            };
        }

        if (type instanceof ArrayType arrayType) {
            var dimensions = Math.max(1, arrayType.getNumDimensions());
            return "[".repeat(dimensions) + getTypeDescriptor(arrayType.getElementType());
        }

        if (type instanceof ClassType classType) {
            return "L" + getClassPath(classType.getName()) + ";";
        }

        throw new RuntimeException("Not implemented for element type '" + type + "'");
    }

    public String getClassName(Type type) {
        if (type instanceof ClassType classType) {
            return classType.getName();
        }

        throw new RuntimeException("Expected class type, got '" + type + "'");
    }

    public String getClassPath(String className) {
        if (className == null || className.isBlank()) {
            className = "Object";
        }

        var resolvedClassName = fullClassnames.get(className);
        if (resolvedClassName == null) {
            resolvedClassName = importer.loadImplicit(className)
                    .map(Class::getName)
                    .orElse(className);
        }

        return resolvedClassName.replace('.', '/');
    }



    public String getModifier(AccessModifier accessModifier) {
        if (accessModifier == AccessModifier.DEFAULT) {
            return "";
        }

        return accessModifier.name().toLowerCase() + " ";
    }

    public String getLoad(Descriptor reg) {
        var prefix = getTypePrefix(reg.getVarType());
        var value = reg.getVirtualReg();

        if (value <= 3 && value >=0) return prefix + "load_" + value;

        return prefix + "load " + value;
    }

    public String getStore(Descriptor reg) {
        var prefix = getTypePrefix(reg.getVarType());
        var value = reg.getVirtualReg();

        if (value <= 3 && value >=0) return prefix + "store_" + value;

        return prefix + "store " + value;
    }
}
