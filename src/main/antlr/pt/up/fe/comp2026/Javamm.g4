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
MOD : '%' ;
OPEN_BRACKET  : '[' ;
CLOSE_BRACKET : ']' ;
OPEN_BRACES : '{' ;
CLOSE_BRACES : '}' ;
OPEN_PARENTHESES : '(' ;
CLOSE_PARENTHESES : ')' ;
AND : '&&';
OR : '||';
LESS_EQUAL: '<=' ;
GREATER_EQUAL: '>=' ;
EQEQ: '==';
NOT_EQUAL: '!=';
LESS_THAN: '<' ;
GREATER_THAN: '>' ;
EQUALS : '=';
NOT: '!';
INC : '++';
DEC : '--';
CLASS : 'class' ;
INT : 'int' ;
BOOLEAN : 'boolean' ;
VOID : 'void' ;
STATIC : 'static' ;
RETURN : 'return' ;
PACKAGE : 'package' ;
PUBLIC : 'public' ;
PRIVATE : 'private' ;
PROTECTED : 'protected' ;
IMPORT : 'import' ;
EXTENDS : 'extends';
IF : 'if' ;
ELSE : 'else' ;
FOR : 'for' ;
DO : 'do' ;
WHILE : 'while' ;
NEW: 'new' ;
THIS: 'this';
TRUE : 'true' ;
FALSE : 'false' ;
STRING : 'string' ;
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
    : PACKAGE path += ID (DOT path +=ID)* SEMI #PackageDeclaration
    ;

classDecl
    : CLASS name=ID (EXTENDS parent=ID)?
        OPEN_BRACES
        (varDecl | methodDecl)*
        CLOSE_BRACES #ClassDeclaration
    ;

varDecl
    : typeNode = type name=ID (EQUALS expr)? SEMI
    ;

param
    : typeNode = type name=ID
    ;

type
    : val = INT (dims += OPEN_BRACKET CLOSE_BRACKET)+ #IntegerArray
    | val = STRING (dims += OPEN_BRACKET CLOSE_BRACKET)+ #StringArray
    | val = ID (dims += OPEN_BRACKET CLOSE_BRACKET)+  #IDArray
    | val = BOOLEAN #Boolean
    | val = INT     #Int
    | val = VOID    #Void
    | val = ID      #Id
    ;

methodDecl locals[boolean isStatic=false]
    : (visibility=PUBLIC | visibility=PRIVATE | visibility=PROTECTED)? 
        (STATIC {$isStatic=true;})?
        typeNode=type name=ID
        OPEN_PARENTHESES (param (COMMA param)*)?  CLOSE_PARENTHESES
        OPEN_BRACES (varDecl | stmt)* CLOSE_BRACES
      #GeneralMethodDecl
    | (visibility=PUBLIC | visibility=PRIVATE | visibility=PROTECTED)?
        (STATIC {$isStatic=true;})?
        VOID name=ID
        OPEN_PARENTHESES STRING OPEN_BRACKET CLOSE_BRACKET args=ID CLOSE_PARENTHESES
        OPEN_BRACES (varDecl | stmt)* CLOSE_BRACES
      #MainMethodDecl
    ;

stmt
    : OPEN_BRACES (stmt)* CLOSE_BRACES #CompoundStmt
    | IF OPEN_PARENTHESES expr CLOSE_PARENTHESES stmt (ELSE stmt)? #IfElseStmt
    | WHILE OPEN_PARENTHESES expr CLOSE_PARENTHESES stmt #WhileStmt
    | DO stmt WHILE OPEN_PARENTHESES expr CLOSE_PARENTHESES SEMI #DoWhileStmt
    | FOR OPEN_PARENTHESES 
        (initVar = ID EQUALS expr)? 
        SEMI expr? SEMI
        (updateVar=ID EQUALS expr | updateVar=ID op=(INC | DEC) | op=(INC | DEC) updateVar=ID )? 
        CLOSE_PARENTHESES stmt 
          #ForStmt
    | expr SEMI #ExprStmt
    | var = expr EQUALS expr SEMI #AssignStmt
    | var = ID (OPEN_BRACKET expr CLOSE_BRACKET)+ EQUALS expr SEMI #ArrayAssignStmt
    | RETURN expr? SEMI #ReturnStmt
    ;

arrayInit : OPEN_BRACES (arrayElem (COMMA arrayElem)*)? CLOSE_BRACES;
arrayElem : expr | arrayInit;

expr
    : OPEN_PARENTHESES expr CLOSE_PARENTHESES #ParenthesesExpr
    | expr OPEN_BRACKET expr CLOSE_BRACKET #ArrayAccess
    | expr DOT name=ID OPEN_PARENTHESES (expr (COMMA expr)*)? CLOSE_PARENTHESES #MethodCall
    | name=ID OPEN_PARENTHESES (expr (COMMA expr)*)? CLOSE_PARENTHESES #ImplicitCall
    | expr DOT name=ID #FieldAccess
    | op= NOT expr #NegationExpr
    | op=(PLUS | MINUS | INC | DEC) expr #UnaryExpr
    | NEW name=ID OPEN_PARENTHESES (expr (COMMA expr)*)? CLOSE_PARENTHESES #NewObject
    | NEW INT (OPEN_BRACKET expr CLOSE_BRACKET)+ #NewArray
    | NEW INT (dims += OPEN_BRACKET CLOSE_BRACKET)+ arrayInit #NewArrayByExtension
    | expr op= (MULTI | DIVISION | MOD) expr #BinaryExpr
    | expr op= (PLUS | MINUS) expr #BinaryExpr
    | expr op= (LESS_THAN | GREATER_THAN | LESS_EQUAL | GREATER_EQUAL) expr #BinaryExpr
    | expr op= (EQEQ | NOT_EQUAL) expr #BinaryExpr
    | expr op= AND expr #BinaryExpr
    | expr op= OR expr #BinaryExpr
    | OPEN_BRACKET (expr (COMMA expr)*)? CLOSE_BRACKET #Array
    | value=INTEGER #IntegerLiteral
    | value=TRUE #BooleanLiteral
    | value=FALSE #BooleanLiteral
    | name=ID #VarRefExpr
    | name=THIS #This
    ;
