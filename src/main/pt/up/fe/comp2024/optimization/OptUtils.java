package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static String getCurrentTemp() {

        return "tmp" + (getNextTempNum() - 1);
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static boolean isInstance(String target, SymbolTable table) {
        return target.equals("this") || target.equals("VarRefExpr");
    }

    public static boolean isClass(String target, SymbolTable table) {
        return table.getImports().contains("[" + target + "]") || target.equals(table.getClassName());
    }

    public static String toOllirType(JmmNode typeNode) {

        //TYPE.checkOrThrow(typeNode);

        String typeName = typeNode.get("declaration");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.getName());
    }

    private static String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            default -> throw new NotImplementedException(typeName);
        };

        return type;
    }


}
