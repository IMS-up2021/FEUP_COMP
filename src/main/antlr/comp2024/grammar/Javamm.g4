grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

DOT : '.';
EQUALS : '=';
SEMI : ';' ;
COLON : ',';
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LBRACKETS : '[' ;
RBRACKETS : ']' ;
MUL : '*' ;
ADD : '+' ;
DIV : '/' ;
SUB : '-' ;
AND : '&&' ;
NEG : '!';
LESS : '<';
IMPORT : 'import';
EXTENDS : 'extends';

STRING : 'String' ;
CLASS : 'class' ;
INT : 'int' ;
BOOL : 'boolean' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
STATIC : 'static' ;
VOID : 'void' ;
MAIN : 'main' ;

WHILE : 'while' ;
ELSE : 'else' ;
IF : 'if' ;



INTEGER : [0] | ([1-9][0-9]*);
ID : [a-zA-Z]+ [a-zA-Z0-9]* ;


SINGLE_COMMENT : DIV DIV .*? '\n' -> skip ;
MULTI_COMMENT : DIV MUL  .*? MUL DIV -> skip ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : stmt + EOF
    | importDeclaration* classDecl EOF
    ;

importDeclaration
    : IMPORT value+=ID (DOT value+=ID)* SEMI #ImportDecl
    ;


classDecl
    : CLASS name=ID
        extendsDecl? // Use the new extendsDecl rule
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;

extendsDecl
    : EXTENDS name=ID // Create a new rule for the extends declaration
    ;


varDecl
    : type name=(ID|MAIN) SEMI //int a;
    | type name=(ID|MAIN) op=LBRACKETS op=RBRACKETS SEMI // int a[];
    ;

type
    : declaration= INT LBRACKETS RBRACKETS #Array
    | declaration= INT '...' #Varchar
    | declaration= BOOL #Bool
    | declaration= INT #Int
    | declaration= STRING #String
    | declaration= ID #Id
    ;


methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN (param(COLON param)*)? RPAREN
        LCURLY (varDecl)* (stmt)* RETURN expr SEMI RCURLY
    | (PUBLIC {$isPublic=true;})?
        STATIC VOID name=MAIN LPAREN STRING LBRACKETS RBRACKETS ID RPAREN LCURLY varDecl* stmt* RCURLY
    ;




param
    : type name=ID
    ;


stmt
    : LCURLY stmt* RCURLY
    | IF LPAREN expr RPAREN stmt ELSE stmt
    | WHILE LPAREN expr RPAREN stmt
    | expr SEMI
    | expr EQUALS expr SEMI //#AssignStmt //
    | ID LBRACKETS expr RBRACKETS EQUALS expr SEMI
    //| RETURN expr SEMI //#ReturnStmt //pode ter varios returns???
    ;





expr
    : op=NEG expr #UnaryExpr
    | expr op= (MUL|DIV) expr #BinaryExpr //
    | expr op= (ADD|SUB) expr #BinaryExpr //
    | expr op=LESS expr #BinaryExpr
    | expr op= AND expr #BinaryExpr
    | expr LBRACKETS expr RBRACKETS #BracketExpr
    | expr DOT 'length' #LengthExpr
    | expr DOT ID LPAREN (expr (COLON expr)* )? RPAREN #MethodCall
    | 'new' INT LBRACKETS expr RBRACKETS #NewBracketExpr
    | 'new' ID LPAREN RPAREN #NewObject
    | LPAREN expr RPAREN #ParentExpr
    | LBRACKETS (expr (COLON expr)*)? RBRACKETS #ArgumentsExpr
    | 'true' #TrueLiteral
    | 'false' #FalseLiteral
    | 'this' #ThisLiteral
    | value=INTEGER #IntegerLiteral //
    | name=(ID|MAIN) #VarRefExpr //
    ;




