grammar Javamm;

@header {
    package pt.up.fe.comp2026;
}

CLASS : 'class' ;

//Types
INT : 'int' ;
BOOLEAN : 'boolean' ;
VOID : 'void' ;

OPEN_BRACKET  : '[' ; // for the array
CLOSE_BRACKET : ']' ; // for the array
TRUE : 'true' ; // boolean values
FALSE : 'false' ; // boolean values

STATIC : 'static' ;
RETURN : 'return' ;
PACKAGE : 'package' ;
PUBLIC : 'public' ;
IMPORT : 'import' ;

IF : 'if' ;
ELSE : 'else' ;
FOR : 'for' ;
WHILE : 'while' ;

INTEGER : '0' | [1-9][0-9]* ;
ID : [$_a-zA-Z][$_a-zA-Z0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

SINGLE_COMMENT: '//' ~[\r\n]*-> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;

program
    : packageDecl importDecl* classNode=classDecl EOF
    ;

importDecl
    : IMPORT path += ID ('.' path += ID)* ';' #ImportDeclaration
    ;

//package is mandatory
packageDecl
    : PACKAGE path += ID ('.' path +=ID)* ';'
    ;

classDecl
    : CLASS name=ID ('extends' parent=ID)?
        '{'
        (varDecl | methodDecl)*
        '}' #ClassDeclaration
    ;

varDecl
    : typeNode = type name=ID ';'
    // typeNode = type name=ID op=OPEN_BRACKET op=CLOSE_BRACKET ';'
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
        '(' (param (',' param)*)?  ')'
        '{' varDecl* stmt* '}'
    ;

stmt
    : '{' (stmt)* '}' #CompoundStmt
    | IF '(' expr ')' stmt (ELSE stmt)? #IfElse
    | WHILE '(' expr ')' stmt #WhileStmt
    | expr ';'              #ExprStmt  //
    | var = ID '=' expr ';' #AssignStmt //
    | RETURN expr ';'       #ReturnStmt
    ;

expr
    : expr '.' ID '(' (expr (',' expr)*)? ')' #MethodCall //
    | expr op= ('*'|'/') expr #BinaryExpr //
    | expr op= ('+'|'-') expr #BinaryExpr //
    | value=INTEGER #IntegerLiteral //
    | name=ID #VarRefExpr
    ;



