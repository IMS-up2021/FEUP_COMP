package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.optimization.OptUtils.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(NEW_OBJECT, this::visitNewObject);
        addVisit(METHOD_CALL, this::visitMethodCall);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitNewObject(JmmNode jmmNode, Void unused) {
        var className = jmmNode.get("name");
        var type = new Type(className, false);
        var ollirType = OptUtils.toOllirType(type);
        var code = "new "  + "(" + className + ")" + ollirType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitMethodCall(JmmNode jmmNode, Void unused) {
        var methodName = jmmNode.get("method");
        var helper = "helper";
        var targetNode = jmmNode.getChildren().get(0);
        String methodTarget = targetNode.getKind();
        if (methodTarget.equals("VarRefExpr")) {
            methodTarget = targetNode.get("name");
        }
        if (methodTarget.equals("ThisLiteral")) {
            helper = "this";
            methodTarget = "this." + table.getClassName();

        }
        StringBuilder code = new StringBuilder();

        Type type = TypeUtils.getExprType(jmmNode, table);
        String ollirType = OptUtils.toOllirType(type);
        String className = table.getClassName();
        String invokeType = "";

        if (isInstance(helper,table)) {
            invokeType = "invokevirtual";
        } else if(isClass(methodTarget,table)){
            invokeType = "invokestatic";
        }else {
            invokeType = "invokespecial";
        }

        code.append(invokeType).append("(").append(methodTarget).append(", ")
                .append("\"").append(methodName).append("\", ");
        for (int i = 1; i < jmmNode.getNumChildren(); i++) {
            code.append(visit(jmmNode.getChildren().get(i)).getCode());
            if (i != jmmNode.getNumChildren() - 1) {
                code.append(", ");
            }
            else {
                code.append(")").append(ollirType);
            }
        }

        code.append(END_STMT);

        /*if (needsHelperVar(jmmNode.getParent())){
            String helperVar = OptUtils.getTemp();
            code.insert(0, helperVar + SPACE + ASSIGN);
            code.append(helperVar).append(ollirType);
        }*/


        return new OllirExprResult(code.toString());
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var rightNode = node.getJmmChild(1);
        var leftNode = node.getJmmChild(0);
        String rhs = "";
        String temp = getTemp();
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        if(needsTemp(rightNode)){
            rhs = temp + resOllirType;
            computation.append(temp).append(resOllirType).append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE).append(visit(rightNode).getCode());
        }
        else{
            rhs = visit(rightNode).getCode();
        }

        var lhs = visit(leftNode);

        code.append(lhs.getCode()).append(SPACE)
                .append(node.get("op")).append(resOllirType).append(SPACE)
                .append(rhs).append(SPACE);

        return new OllirExprResult(String.valueOf(code), computation);
    }




    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        String varName = node.get("name");
        // Generate some OLLIR code for illustrative purposes
        // get type from symbol table
        Type type = TypeUtils.getExprType(node, table);
        // Convert the type to OLLIR format
        String ollirType = OptUtils.toOllirType(type);
        // Generate OLLIR code
        String ollirCode = varName + ollirType;
        return new OllirExprResult(ollirCode);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
