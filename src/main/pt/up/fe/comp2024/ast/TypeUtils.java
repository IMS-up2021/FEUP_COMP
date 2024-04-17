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
    public static String getFloatTypeName(){ return FLOAT_TYPE_NAME; }

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

            default -> throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
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
        String varName = varRefExpr.get("name");
        // Checks if the variable is present in the local variables of the current method
        for (Symbol localVar : table.getLocalVariables(table.getClassName() + "." + varName)) {
            if (localVar.getName().equals(varName)) {
                return localVar.getType();
            }
        }
        // Checks if the variable is present in the parameters of the current method
        for (Symbol param : table.getParameters(table.getClassName() + "." + varName)) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }
        // Checks whether the variable is present in the fields of the current class or its superclasses
        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }
        // Checks if the variable is present in imports
        for (String imported : table.getImports()) {
            if (imported.endsWith("." + varName)) {
                return new Type(imported, false);
            }
        }
        throw new RuntimeException("Variable '" + varName + "' is undeclared");
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

