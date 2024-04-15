package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
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
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {
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

    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String varName = varRefExpr.get("name");
        String currentMethod;

        if (table.getLocalVariables(varName).contains(varName)) {
            return new Type(varName, false);
        }

        if (table.getParameters(varName).contains(varName)) {
            return new Type(varName, false);
        }

        if (table.getFields().contains(varName)) {
            return new Type(varName, false);
        }

        if (table.getImports().contains(varName)) {
            return new Type(varName, false);
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

