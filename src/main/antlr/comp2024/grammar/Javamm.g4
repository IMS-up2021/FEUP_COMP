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

STRING : 'string' ;
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



INTEGER : [0-9] ;
ID : [a-zA-Z]+ ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : importDeclaration* classDecl EOF
    ;

importDeclaration
    : IMPORT ID (DOT ID)* SEMI
    ;


classDecl
    : CLASS name=ID
        (EXTENDS ID)?
        LCURLY
        varDecl*
        methodDecl*
        RCURLY
    ;




varDecl
    : type name=ID SEMI
    ;

type
    : name= INT LBRACKETS RBRACKETS
    //| name= INT
    | name= BOOL
    | name= INT
    | name= ID ;


methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        LPAREN (param(COLON param)*)? RPAREN
        LCURLY varDecl* stmt* RETURN expr SEMI RCURLY
    | (PUBLIC {$isPublic=true;})?
        STATIC VOID MAIN LPAREN STRING LBRACKETS RBRACKETS name=ID RPAREN LCURLY varDecl* stmt* RCURLY
    ;




param
    : type name=ID
    ;


stmt
    : LCURLY stmt* RCURLY
    | IF LPAREN expr RPAREN stmt ELSE stmt
    | WHILE LPAREN expr RPAREN stmt ELSE stmt
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
    | expr DOT ID LPAREN (expr (COLON expr)* )? RPAREN #MethodExpr
    | 'new' INT LBRACKETS expr RBRACKETS #NewBracketExpr
    | 'new' ID LPAREN RPAREN #NewObject
    | LPAREN expr RPAREN #ParentExpr
    | LBRACKETS (expr (COLON expr)*)? RBRACKETS #ArgumentsExpr
    | 'true' #TrueLiteral
    | 'false' #FalseLiteral
    | 'this' #ThisLiteral
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr //
    ;




