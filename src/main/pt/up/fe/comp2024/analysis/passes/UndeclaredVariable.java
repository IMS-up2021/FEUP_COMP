package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import static com.sun.source.tree.Tree.Kind.ARRAY_ACCESS;
import static pt.up.fe.comp2024.ast.TypeUtils.*;

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
        addVisit(Kind.UNARY_EXPR, this::checkUnaryExpr);
        addVisit(Kind.ARRAY_ACCESS, this::checkArrayAccess);
        addVisit(Kind.ASSIGN_STMT, this::checkAssignment);
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
        try {
            Type resultType = getBinExprType(binaryExpr, table);
        } catch (RuntimeException e) {
            addError(e.getMessage(), binaryExpr);
        }

        return null;
    }

    private Void checkUnaryExpr(JmmNode exprNode, SymbolTable table) {
        try {
            Type resultType = getUnaryExprType(exprNode, table);
        } catch (RuntimeException e) {
            addError(e.getMessage(), exprNode);
        }
        return null;
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

    private Void checkArrayAccess(JmmNode arrayAccessNode, SymbolTable table) {
        JmmNode arrayExpr = arrayAccessNode.getChildren().get(0);
        JmmNode indexExpr = arrayAccessNode.getChildren().get(1);
        Type arrayType = getVarExprType(arrayExpr, table);
        if (!arrayType.isArray()) {
            addError("Array access is done over a non-array type", arrayAccessNode);
        }
        Type indexType = getVarExprType(indexExpr, table);
        if (!"int".equals(indexType.getName())) {
            addError("Array access index is not of integer type", indexExpr);
        }
        return null;
    }

    private Void checkAssignment(JmmNode node, SymbolTable table) {
        JmmNode leftOperand = node.getChildren().get(0);
        Type leftType = getVarExprType(leftOperand, table);

        JmmNode rightOperand = node.getChildren().get(1);
        Type rightType = getVarExprType(rightOperand, table);

        if (!isAssignable(leftType, rightType)) {
            addError("Type of the assignee is not compatible with the assigned", node);
        }

        return null;
    }


    private boolean isAssignable(Type leftType, Type rightType) {
        // If either type is null, assignment is not possible
        if (leftType == null || rightType == null) {
            return false;
        }
        // Use the areTypesAssignable function to check whether types are assignable
        return areTypesAssignable(rightType, leftType);
    }
}