package lox_interpreter_java;

import java.util.ArrayList;
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
            statements.add(statement());
        }

        return statements;
    }

    /*
     * Returns an equality expression.
     */
    private Expr expression()
    {
        return equality();
    }

    /*
     * Parses a single statement. Prints it if it is a 
     * PRINT statement. Returns it otherwise.
     */
    private Stmt statement()
    {
        if (match(TokenType.PRINT))
            return printStatement();
        
        return expressionStatement();
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
     * Parses an expression followed by a semicolon.
     */
    private Stmt expressionStatement()
    {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "Expect ';' after expression.");

        return new Stmt.Expression(expr);
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

        return primary();
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

        if (match(TokenType.LEFT_PAREN))
        {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }
}
