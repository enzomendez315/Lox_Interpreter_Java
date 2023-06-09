package lox_interpreter_java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * This class is used for parsing tokens once they have been scanned.
 * Each method for parsing a grammar rule produces a syntax tree for 
 * that rule and returns it to the caller.
 */
public class Parser 
{
    private static class ParseError extends RuntimeException{}

    private final List<Token> tokens;
    private int current = 0;    // Points to the next token to be parsed.

    /*
     * Constructs a Parser object.
     */
    public Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    /*
     * Parses each statement in the file and returns them 
     * as a list.
     */
    public List<Stmt> parse()
    {
        List<Stmt> statements = new ArrayList<>();

        while (!isAtEnd())
        {
            statements.add(declaration());
        }

        return statements;
    }

    /*
     * Returns an equality expression.
     */
    private Expr expression()
    {
        return assignment();
    }

    /*
     * Looks for a function or variable declaration, or parses 
     * the next statement if there aren't any.
     */
    private Stmt declaration()
    {
        try
        {
            if (match(TokenType.CLASS))
                return classDeclaration();
            
            if (match(TokenType.FUN))
                return function("function");
            
            if (match(TokenType.VAR))
                return varDeclaration();

            return statement();
        }
        catch (ParseError error)
        {
            synchronize();

            return null;
        }
    }

    /*
     * Consumes the name of a class and parses its method 
     * declarations.
     */
    private Stmt classDeclaration()
    {
        Token name = consume(TokenType.IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(TokenType.LESS))
        {
            consume(TokenType.IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }

        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd())
        {
            methods.add(function("method"));
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, superclass, methods);
    }

    /*
     * Parses a single statement and executes the correct 
     * method given the type.
     */
    private Stmt statement()
    {
        if (match(TokenType.FOR))
            return forStatement();
        
        if (match(TokenType.IF))
            return ifStatement();
        
        if (match(TokenType.PRINT))
            return printStatement();

        if (match(TokenType.RETURN))
            return returnStatement();

        if (match(TokenType.WHILE))
            return whileStatement();

        if (match(TokenType.LEFT_BRACE))
            return new Stmt.Block(block());
        
        return expressionStatement();
    }

    /*
     * Parses all the pieces of a 'for' loop and executes 
     * the body of the loop.
     */
    private Stmt forStatement()
    {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");

        // For initializer
        Stmt initializer;
        if (match(TokenType.SEMICOLON))
            initializer = null;
        else if (match(TokenType.VAR))
            initializer = varDeclaration();
        else 
            initializer = expressionStatement();

        // For condition
        Expr condition = null;
        if (!check(TokenType.SEMICOLON))
            condition = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

        // For increment
        Expr increment = null;
        if (!check(TokenType.RIGHT_PAREN))
            increment = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");
        
        // For body
        Stmt body = statement();

        // Advances loop
        if (increment != null)
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        if (condition == null)
            condition = new Expr.Literal(true); // For infinite loops
        body = new Stmt.While(condition, body);

        if (initializer != null)
            body = new Stmt.Block(Arrays.asList(initializer, body));

        return body;
    }

    /*
     * Checks that the 'if' condition is in parenthesis and 
     * parses the statement.
     */
    private Stmt ifStatement()
    {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE))
            elseBranch = statement();

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /*
     * Checks that the statement ends with a semicolon and 
     * prints it.
     */
    private Stmt printStatement()
    {
        Expr value = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after value.");

        return new Stmt.Print(value);
    }

    /*
     * Parses a value expression if there exists a return 
     * value or null if there isn't one.
     */
    private Stmt returnStatement()
    {
        Token keyword = previous();
        Expr value = null;
        if (!check(TokenType.SEMICOLON))
            value = expression();

        consume(TokenType.SEMICOLON, "Expect ';' after return value.");

        return new Stmt.Return(keyword, value);
    }

    /*
     * Consumes an identifier token for the variable name, 
     * then it parses the initializer expression or leaves it 
     * as null.
     */
    private Stmt varDeclaration()
    {
        Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(TokenType.EQUAL))
            initializer = expression();

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    /*
     * Checks that the 'while' condition is in parenthesis 
     * and parses the statement.
     */
    private Stmt whileStatement()
    {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    /*
     * Parses an expression followed by a semicolon.
     */
    private Stmt expressionStatement()
    {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after expression.");

        return new Stmt.Expression(expr);
    }

    /*
     * Checks that the function is declared correctly and consumes 
     * each part of the function.
     */
    private Stmt.Function function(String kind)
    {
        Token name = consume(TokenType.IDENTIFIER, "Expect " + kind + " name.");

        consume(TokenType.LEFT_PAREN, "Expect '(' after " + kind + " name.");

        List<Token> parameters = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN))
        {
            do
            {
                if (parameters.size() >= 255)
                    error(peek(), "Can't have more than 255 parameters.");

                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
            }
            while (match(TokenType.COMMA));
        }

        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");

        consume(TokenType.LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        
        return new Stmt.Function(name, parameters, body);
    }

    /*
     * Parses statements and adds them to a list until 
     * it reaches the end of the block.
     */
    private List<Stmt> block()
    {
        List<Stmt> statements = new ArrayList<>();

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd())
        {
            statements.add(declaration());
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");

        return statements;
    }

    /*
     * Parses an assignment expression.
     */
    private Expr assignment()
    {
        Expr expr = or();

        if (match(TokenType.EQUAL))
        {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable)
            {
                Token name = ((Expr.Variable)expr).name;

                return new Expr.Assign(name, value);
            }
            else if (expr instanceof Expr.Get)
            {
                Expr.Get get = (Expr.Get)expr;

                return new Expr.Set(get.object, get.name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    /*
     * Parses 'or' expressions.
     */
    private Expr or()
    {
        Expr expr = and();

        while (match(TokenType.OR))
        {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /*
     * Parses 'and' expressions.
     */
    private Expr and()
    {
        Expr expr = equality();

        while (match(TokenType.AND))
        {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /*
     * Checks if the series of tokens is != or == and returns 
     * a binary syntax tree.
     */
    private Expr equality()
    {
        Expr expr = comparison();

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL))
        {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * Checks if the current token has any of the given types.
     * If so, it consumes the token and returns true.
     * False otherwise.
     */
    private boolean match(TokenType... types)
    {
        for (TokenType type : types)
        {
            if (check(type))
            {
                advance();
                return true;
            }
        }

        return false;
    }

    /*
     * Checks that the next token is of the expected type. If so, it 
     * consumes it. Otherwise, it throws and error.
     */
    private Token consume(TokenType type, String message)
    {
        if (check(type))
            return advance();

        throw error(peek(), message);
    }

    /*
     * Returns true if the current token is of the given type. False otherwise.
     * Unlike match(), it looks at the token but does not consume it.
     */
    private boolean check(TokenType type)
    {
        if (isAtEnd())
            return false;
        
        return peek().type == type;
    }

    /*
     * Consumes the current token and returns it.
     */
    private Token advance()
    {
        if (!isAtEnd())
            current++;

        return previous();
    }

    /*
     * Checks if there are no more tokens to consume.
     */
    private boolean isAtEnd()
    {
        return peek().type == TokenType.EOF;
    }

    /*
     * Returns the current token that is not yet consumed.
     */
    private Token peek()
    {
        return tokens.get(current);
    }

    /*
     * Returns the most recently consumed token.
     */
    private Token previous()
    {
        return tokens.get(current - 1);
    }

    /*
     * Returns an error containing the token and the message for the error.
     */
    private ParseError error(Token token, String message)
    {
        Lox.error(token, message);

        return new ParseError();
    }

    /*
     * Discards tokens until it gets to the next statement.
     */
    private void synchronize()
    {
        advance();

        while(!isAtEnd())
        {
            if (previous().type == TokenType.SEMICOLON)
                return;

            switch (peek().type)
            {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                return;
            }

            advance();
        }
    }

    /*
     * Checks if the series of tokens is <, <=, >, or >= and returns a 
     * comparison syntax tree.
     */
    private Expr comparison()
    {
        Expr expr = term();

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL))
        {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * Checks if the token is + or - and returns a binary
     * syntax tree.
     */
    private Expr term()
    {
        Expr expr = factor();

        while (match(TokenType.MINUS, TokenType.PLUS))
        {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * Checks if the token is * or / and returns a binary
     * syntax tree.
     */
    private Expr factor()
    {
        Expr expr = unary();

        while (match(TokenType.SLASH, TokenType.STAR))
        {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /*
     * Checks if the token is ! or - and returns a unary 
     * syntax tree.
     */
    private Expr unary()
    {
        if (match(TokenType.BANG, TokenType.MINUS))
        {
            Token operator = previous();
            Expr right = unary();

            return new Expr.Unary(operator, right);
        }

        return call();
    }

    /*
     * Parses a primary expression and then each call expression 
     * using the previously parsed expression as the callee.
     */
    private Expr call()
    {
        Expr expr = primary();

        while (true)
        {
            if (match(TokenType.LEFT_PAREN))
                expr = finishCall(expr);
            else if (match(TokenType.DOT))
            {
                Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'");
                expr = new Expr.Get(expr, name);
            }
            else 
                break;
        }

        return expr;
    }

    /*
     * Parses each of the arguments from the method or looks for 
     * ')' if there aren't any. Then it builds a syntax tree.
     */
    private Expr finishCall(Expr callee)
    {
        List<Expr> arguments = new ArrayList<>();
        if (!check(TokenType.RIGHT_PAREN))
        {
            do
            {
                if (arguments.size() >= 255)
                    error(peek(), "Can't have more than 255 arguments.");
                
                arguments.add(expression());
            }
            while (match(TokenType.COMMA));
        }

        Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments");

        return new Expr.Call(callee, paren, arguments);
    }

    /*
     * Checks if the series of tokens is "false", "true", "null", 
     * a number, or ( and returns the appropriate syntax tree.
     */
    private Expr primary()
    {
        if (match(TokenType.FALSE))
            return new Expr.Literal(false);

        if (match(TokenType.TRUE))
            return new Expr.Literal(true);

        if (match(TokenType.NIL))
            return new Expr.Literal(null);

        if (match(TokenType.NUMBER, TokenType.STRING))
            return new Expr.Literal(previous().literal);

        if (match(TokenType.SUPER))
        {
            Token keyword = previous();
            consume(TokenType.DOT, "Expect '.' after 'super'.");
            Token method = consume(TokenType.IDENTIFIER, "Expect superclass method name.");

            return new Expr.Super(keyword, method);
        }

        if (match(TokenType.THIS))
            return new Expr.This(previous());

        if (match(TokenType.IDENTIFIER))
            return new Expr.Variable(previous());

        if (match(TokenType.LEFT_PAREN))
        {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }
}
