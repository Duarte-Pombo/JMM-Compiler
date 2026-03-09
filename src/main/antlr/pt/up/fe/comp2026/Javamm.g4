grammar Javamm;

@header {
    package pt.up.fe.comp2026;
}

SEMI : ';' ;
COMMA : ',' ;
DOT : '.' ;
PLUS : '+' ;
MINUS : '-' ;
MULTI : '*' ;
DIVISION : '/' ;
OPEN_BRACKET  : '[' ;
CLOSE_BRACKET : ']' ;
OPEN_BRACES : '{' ;
CLOSE_BRACES : '}' ;
OPEN_PARENTHESES : '(' ;
CLOSE_PARENTHESES : ')' ;
LESS_EQUAL: '<=' ;
GREATER_EQUAL: '>=' ;
EQEQ: '==';
NOT_EQUAL: '!=';
LESS_THAN: '<' ;
GREATER_THAN: '>' ;
EQUALS : '=';

CLASS : 'class' ;

INT : 'int' ;
BOOLEAN : 'boolean' ;
VOID : 'void' ;
STATIC : 'static' ;
RETURN : 'return' ;
PACKAGE : 'package' ;
PUBLIC : 'public' ;
IMPORT : 'import' ;
EXTENDS : 'extends';

IF : 'if' ;
ELSE : 'else' ;
FOR : 'for' ;
WHILE : 'while' ;
NEW: 'new' ;

TRUE : 'true' ;
FALSE : 'false' ;
INTEGER : '0' | [1-9][0-9]* ;
ID : [$_a-zA-Z][$_a-zA-Z0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

SINGLE_COMMENT: '//' ~[\r\n]*-> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;

program
    : packageDecl importDecl* classNode=classDecl EOF
    ;

importDecl
    : IMPORT path += ID (DOT path += ID)* SEMI #ImportDeclaration
    ;

//package is mandatory
packageDecl
    : PACKAGE path += ID (DOT path +=ID)* SEMI
    ;

classDecl
    : CLASS name=ID (EXTENDS parent=ID)?
        OPEN_BRACES
        (varDecl | methodDecl)*
        CLOSE_BRACES #ClassDeclaration
    ;

varDecl
    : typeNode = type name=ID SEMI
    // typeNode = type name=ID op=OPEN_BRACKET op=CLOSE_BRACKET SEMI
    ;

param
    : typeNode = type name=ID
    ;

type
    : type OPEN_BRACKET CLOSE_BRACKET #Array
    | val = BOOLEAN #Boolean
    | val = INT     #Int
    | val = VOID    #Void
    | val = ID      #Id
    ;

methodDecl locals[boolean isStatic=false]
    : (visibility=PUBLIC)? (STATIC {$isStatic=true;})?
        returnType = type name=ID
        OPEN_PARENTHESES (param (COMMA param)*)?  CLOSE_PARENTHESES
        OPEN_BRACES varDecl* stmt* CLOSE_BRACES
    ;

stmt
    : OPEN_BRACES (stmt)* CLOSE_BRACES #CompoundStmt
    | IF OPEN_PARENTHESES expr CLOSE_PARENTHESES stmt (ELSE stmt)? #IfElse
    | WHILE OPEN_PARENTHESES expr CLOSE_PARENTHESES stmt #WhileStmt
    | expr SEMI              #ExprStmt
    | var = ID EQUALS expr SEMI #AssignStmt
    | RETURN expr? SEMI       #ReturnStmt
    ;

expr
    : OPEN_PARENTHESES expr CLOSE_PARENTHESES #ParenthesesExpr
    | expr DOT name=ID OPEN_PARENTHESES (expr (COMMA expr)*)? CLOSE_PARENTHESES #MethodCall
    | NEW name=ID OPEN_PARENTHESES CLOSE_PARENTHESES  #NewObject
    | expr op= (MULTI | DIVISION) expr #BinaryExpr
    | expr op= (PLUS | MINUS) expr #BinaryExpr
    | expr op=(LESS_THAN | GREATER_THAN) expr #BinaryOp
    | expr op=(LESS_EQUAL | GREATER_EQUAL | EQEQ | NOT_EQUAL) expr #BinaryOp
    | value=INTEGER #IntegerLiteral
    | name=ID #VarRefExpr
    ;
