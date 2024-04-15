package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.BINARY_EXPR, this::checkBinaryExpression);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String varRefName = varRefExpr.get("name");

        // Check if variable is declared in current method
        if (!isDeclaredLocally(varRefName, table)) {
            // Check if variable is a field of the class
            if (!isField(varRefName, table)) {
                // Check if variable is a parameter of the current method
                if (!isParameter(varRefName, table)) {
                    // Check if variable is an imported class
                    if (!isImportedClass(varRefName, table)) {
                        // Variable is undeclared
                        addUndeclaredVariableError(varRefExpr, varRefName);
                    }
                }
            }
        }

        return null;
    }

    private boolean isDeclaredLocally(String varName, SymbolTable table) {
        return table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varName));
    }

    private boolean isField(String varName, SymbolTable table) {
        return table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varName));
    }

    private boolean isParameter(String varName, SymbolTable table) {
        return table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varName));
    }

    private boolean isImportedClass(String varName, SymbolTable table) {
        return table.getImports().stream()
                .anyMatch(importedClass -> importedClass.endsWith("." + varName));
    }

    private Void checkBinaryExpression(JmmNode binaryExpr, SymbolTable table) {
        JmmNode leftOperand = binaryExpr.getChildren().get(0);
        JmmNode rightOperand = binaryExpr.getChildren().get(1);
        String operationKindString = binaryExpr.getKind();
        Kind operationKind = Kind.fromString(operationKindString);

        switch (operationKind) {
            case ADD:
                if (!isInt(leftOperand, table) || !isInt(rightOperand, table)) {
                    addError("Operands of ADD operation must be of type int", binaryExpr);
                }
                break;
            case SUB:
                if (!isInt(leftOperand, table) || !isInt(rightOperand, table)) {
                    addError("Operands of SUB operation must be of type int", binaryExpr);
                }
                break;
            case MUL:
                if (!isInt(leftOperand, table) || !isInt(rightOperand, table)) {
                    addError("Operands of MUL operation must be of type int", binaryExpr);
                }
                break;
            case DIV:
                if (!isInt(leftOperand, table) || !isInt(rightOperand, table)) {
                    addError("Operands of DIV must be of type int", binaryExpr);
                }
                break;
            case AND:
                if (!isBoolean(leftOperand, table) || !isBoolean(rightOperand, table)) {
                    addError("Operands of logical AND operation must be of type boolean", binaryExpr);
                }
                break;
            case LESS:
                if (!isInt(leftOperand, table) || !isInt(rightOperand, table)) {
                    addError("Operands of less-than operation must be of type int", binaryExpr);
                }
                break;
            case MORE:
                if (!isInt(leftOperand, table) || !isInt(rightOperand, table)) {
                    addError("Operands of more-than operation must be of type int", binaryExpr);
                }
                break;
            case OR:
                if (!isBoolean(leftOperand, table) || !isBoolean(rightOperand, table)) {
                    addError("Operands of logical OR operation must be of type boolean", binaryExpr);
                }
                break;
        }

        return null;
    }

    private boolean isInt(JmmNode node, SymbolTable table) {
        if (node.getKind().equals("INTEGER_LITERAL")) {
            return true;
        }
        if (node.getKind().equals("INT")) {
            return true;
        }
        return false;
    }

    private boolean isBoolean(JmmNode node, SymbolTable table) {
        if (node.getKind().equals("BOOL")) {
            return true;
        }
        if (node.getKind().equals("TRUE_LITERAL") || node.getKind().equals("FALSE_LITERAL")) {
            return true;
        }
        return false;
    }

    private boolean isOp(JmmNode node, SymbolTable table){
        return true;
    }

    private void addError(String message, JmmNode node) {
        int line = NodeUtils.getLine(node);
        int column = NodeUtils.getColumn(node);
        Report errorReport = Report.newError(Stage.SEMANTIC, line, column, message, null);
        addReport(errorReport);
    }


    private void addUndeclaredVariableError(JmmNode varRefExpr, String varName) {
        var message = String.format("Variable '%s' is undeclared.", varName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                message,
                null)
        );
    }

    /*
    private void checkExpression(JmmNode exprNode, SymbolTable table) {
        switch (exprNode.getKind()) {
            case "ARRAY_ACCESS":
                checkArrayAccess(exprNode, table);
                break;
            default:
                // Handle other expression types if needed
                break;
        }
    }

    private void checkArrayAccess(JmmNode arrayAccessNode, SymbolTable table) {
        JmmNode arrayExpr = arrayAccessNode.getChildren().get(0);
        JmmNode indexExpr = arrayAccessNode.getChildren().get(1);

        if (!isArray(arrayExpr, table)) {
            addError("Array access is done over a non-array type", arrayAccessNode);
        }
        if (!isInt(indexExpr, table)) {
            addError("Array access index is not of integer type", indexExpr);
        }
    }

    private boolean isArray(JmmNode exprNode, SymbolTable table) {
        String exprType = table.getExpressionType(exprNode);

        return exprType != null && exprType.endsWith("[]");
    }

    private void checkAssignment(JmmNode assignNode, JmmSymbolTable symbolTable) {
        JmmNode leftExpr = assignNode.getChildren().get(0);
        JmmNode rightExpr = assignNode.getChildren().get(1);

        String leftExprType = symbolTable.getExpressionType(leftExpr);
        String rightExprType = symbolTable.getExpressionType(rightExpr);

        if (!isAssignable(leftExprType, rightExprType)) {
            addError("Type of the assignee is not compatible with the assigned", assignNode);
        }
    }

    private void checkCondition(JmmNode conditionNode, JmmSymbolTable symbolTable) {
        String conditionType = symbolTable.getExpressionType(conditionNode);

        if (!"boolean".equals(conditionType)) {
            addError("Expression in condition must return a boolean", conditionNode);
        }
    }

    private void checkThisExpression(JmmNode thisExprNode, JmmSymbolTable symbolTable) {
        if (symbolTable.isStaticMethod()) {
            addError("Cannot use 'this' expression in a static method", thisExprNode);
        }
    }

    private void checkThisAsObject(JmmNode thisExprNode, JmmSymbolTable symbolTable) {
        String className = symbolTable.getClassName();
        String exprClassName = symbolTable.getExpressionType(thisExprNode);

        if (exprClassName != null && !className.equals(exprClassName) && !symbolTable.isSuperClass(className, exprClassName)) {
            addError("Cannot use 'this' as an object in this context", thisExprNode);
        }
    }

    private boolean isAssignable(String leftType, String rightType) {
        // Implementar lógica para verificar se o tipo do operando à esquerda é compatível com o tipo do operando à direita
    }
    */
}