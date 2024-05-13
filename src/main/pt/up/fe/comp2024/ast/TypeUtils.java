package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

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
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr, table);
            case UNARY_EXPR -> getUnaryExprType(expr, table);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    public static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {
        JmmNode leftOperand = binaryExpr.getChildren().get(0);
        JmmNode rightOperand = binaryExpr.getChildren().get(1);
        String operator = binaryExpr.get("op");

        Type leftType = getExprType(leftOperand, table);
        Type rightType = getExprType(rightOperand, table);

        switch (operator) {
            case "+", "-", "*", "/" -> {
                if (!leftType.getName().equals(INT_TYPE_NAME) || !rightType.getName().equals(INT_TYPE_NAME)) {
                    throw new RuntimeException("Operands of arithmetic expressions must be of type 'int'");
                }
                return new Type(INT_TYPE_NAME, false);
            }

            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        }
    }

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

    public static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        if ("ArrayInitExpr".equals(varRefExpr.getKind())) {
            return new Type("int", true);
        }
        if ("TrueLiteral".equals(varRefExpr.getKind()) || "FalseLiteral".equals(varRefExpr.getKind())) {
            return new Type("boolean", false);
        }
        if ("MethodCall".equals(varRefExpr.getKind())) {
            String methodName = varRefExpr.get("method");
            Type returnType = table.getReturnType(methodName);
            return new Type(returnType.getName(), returnType.isArray());
        }
        String varName;
        switch (varRefExpr.getKind()) {
            case "ThisLiteral":
                varName = varRefExpr.getParent().get("method");
                break;
            case "IntegerLiteral":
                varName = varRefExpr.get("value");
                return new Type(varName, false);
            case "NewBracketExpr":
                varName = varRefExpr.getChild(0).get("value");
                return new Type(varName, true);
            case "BinaryExpr":
                String operator = varRefExpr.get("op");
                JmmNode leftOperand = varRefExpr.getChildren().get(0);
                JmmNode rightOperand = varRefExpr.getChildren().get(1);
                Type leftType = getVarExprType(leftOperand, table);
                Type rightType = getVarExprType(rightOperand, table);

                switch (operator) {
                    case "+":
                    case "-":
                    case "*":
                    case "/":
                        // Arithmetic operators
                        if (leftType.getName().equals("int") && rightType.getName().equals("int")) {
                            return new Type("int", false);
                        } else {
                            throw new RuntimeException("Incompatible types for arithmetic operation: " + leftType + " and " + rightType);
                        }
                }
            default:
                varName = varRefExpr.get("name");
                break;
        }
        // Check local variables
        JmmNode parentNode = varRefExpr.getParent();
        while (!"MethodDecl".equals(parentNode.getKind())) {
            parentNode = parentNode.getParent();
        }
        String parentMethodName = parentNode.get("name");
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
        return null; //throw new RuntimeException("Variable '" + varName + "' is undeclared");
    }

    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        return sourceType.getName().equals(destinationType.getName());
    }
}

