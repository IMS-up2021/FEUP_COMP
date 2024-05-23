package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.Objects;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String FLOAT_TYPE_NAME = "float";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    public static String getFloatTypeName() {
        return FLOAT_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */

    // Baseia-se no tipo (kind) da expressão e chama a função apropriada para determinar o tipo específico (getBinExprType, getUnaryExprType, getVarExprType)
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case METHOD_CALL -> getMethodCallType(expr, table);
            case BINARY_EXPR -> getBinExprType(expr, table);
            case UNARY_EXPR -> getUnaryExprType(expr, table);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case BRACKET_EXPR -> getBracketExprType(expr, table);
            case NEW_BRACKET_EXPR -> getNewBracketExprType(expr, table);
            case NEW_OBJECT -> new Type(expr.get("name"), false);
            case SIMPLE_EXPR_STMT -> getExprType(expr.getChild(0), table);
            case ARRAY_INIT_EXPR -> new Type("int", true);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case THIS_LITERAL -> new Type(table.getClassName(), false);
            case TRUE_LITERAL, FALSE_LITERAL -> new Type("boolean", false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBracketExprType(JmmNode expr, SymbolTable table) {
        String parentMethodName;
        JmmNode firstChild = expr.getChildren().get(0);
        Type firstType = getVarExprType(firstChild, table);
        if (!firstType.isArray()) {
            return new Type("INVALIDARRACC", false);
        }
        if ("VarRefExpr".equals(firstChild.getKind())) {
            String arrayName = firstChild.get("name");

            JmmNode parentNode = expr.getParent();
            while (!"MethodDecl".equals(parentNode.getKind())) {
                parentNode = parentNode.getParent();
            }
            parentMethodName = parentNode.get("name");
            for (Symbol localVar : table.getLocalVariables(parentMethodName)) {
                if (localVar.getName().equals(arrayName)) {
                    String typeName = localVar.getType().getName().replaceFirst("V$", "");
                    return new Type(typeName, false);
                }
            }
            for (Symbol param : table.getParameters(parentMethodName)) {
                if (param.getName().equals(arrayName)) {
                    String typeName = param.getType().getName().replaceFirst("V$", "");
                    return new Type(typeName, false);
                }
            }
            for (Symbol field : table.getFields()) {
                if (field.getName().equals(arrayName)) {
                    String typeName = field.getType().getName().replaceFirst("V$", "");
                    return new Type(typeName, true);
                }
            }
        }
        return new Type("INVALIDARRACC", false);
    }

    private static Type getNewBracketExprType(JmmNode expr, SymbolTable table) {
        var varName = expr.getChild(0).get("value");
        return new Type(varName, true);
    }

    // Verifica os operandos e o operador da expressão binária. Garante que ambos os operandos são do tipo int para operadores aritméticos.
    public static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {
        JmmNode leftOperand = binaryExpr.getChildren().get(0);
        JmmNode rightOperand = binaryExpr.getChildren().get(1);
        String operator = binaryExpr.get("op");

        Type leftType = getExprType(leftOperand, table);
        Type rightType = getExprType(rightOperand, table);

        switch (operator) {
            case "+", "-", "*", "/" -> {
                if (!leftType.getName().equals(INT_TYPE_NAME) || !rightType.getName().equals(INT_TYPE_NAME)) {
                    return new Type("INVALIDBINOP", false);
                }
                return new Type(INT_TYPE_NAME, false);
            }

            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        }
    }

    // Verifica se o tipo do operando é compatível com a expressão unária. Retorna o tipo do operando se for compatível.
    public static Type getUnaryExprType(JmmNode expr, SymbolTable table) {
        JmmNode operand = expr.getChildren().get(0);
        Type operandType = getExprType(operand, table);

        if (!isCompatibleWithUnaryExpr(operandType)) {
            throw new RuntimeException("Unary expression operand must have compatible type");
        }

        return operandType;
    }

    private static boolean isCompatibleWithUnaryExpr(Type type) {
        return INT_TYPE_NAME.equals(type.getName()) || FLOAT_TYPE_NAME.equals(type.getName());
    }

    public static Type getMethodCallType(JmmNode methodCall, SymbolTable table) {
        String methodName = methodCall.get("method");

        // Check methods
        if (table.getMethods().contains(methodName)) {
            return table.getReturnType(methodName);
        }

        // Check the type of the target object
        JmmNode target = methodCall.getChildren().get(0);

        Type targetType = getExprType(target, table);
        if (targetType == null) {
            throw new IllegalArgumentException("Target type cannot be null");
        }

        String formattedTargetTypeName = "[" + targetType.getName() + "]";


        // Check if the target type is in the imports
        if (table.getImports().contains(formattedTargetTypeName)) {
            return new Type("imported", false);
        }

        return null;
    }

    public static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {

        String varName = varRefExpr.get("name");
        String parentMethodName;
        // Check local variables
        JmmNode parentNode = varRefExpr.getParent();
        while (!"MethodDecl".equals(parentNode.getKind())) {
            parentNode = parentNode.getParent();
        }
        parentMethodName = parentNode.get("name");
        for (Symbol localVar : table.getLocalVariables(parentMethodName)) {
            if (localVar.getName().equals(varName)) {
                return localVar.getType();
            }
        }
        // Check parameters
        for (Symbol param : table.getParameters(parentMethodName)) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }
        // Check fields
        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }
        // Check imports
        for (String imported : table.getImports()) {
            if (imported.endsWith(", " + varName + "]") || imported.equals("[" + varName + "]")) {
                return new Type(varName, false);
            }
        }
        // Check methods
        if (table.getMethods().contains(varName)) {
            return table.getReturnType(varName);
        }
        // Check class name
        if (table.getClassName().equals(varName)) {
            return new Type(table.getClassName(), false);
        }
        // Variable not found
        throw new IllegalArgumentException("Variable '" + varName + "' not found");
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        if (sourceType == null || destinationType == null || destinationType.getName() == null || sourceType.getName() == null) {
            return false;
        }

        if (sourceType.getName().equals("imported") || destinationType.getName().equals("imported")) {
            return true;
        }

        return sourceType.getName().equals(destinationType.getName());
    }
}

