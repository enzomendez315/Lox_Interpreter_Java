package lox_interpreter_java;

import java.util.List;

/*
 * This class is used for parsing tokens once they have been scanned.
 * Each method for parsing a grammar rule produces a syntax tree for 
 * that rule and returns it to the caller.
 */
public class Parser 
{
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
     * Returns an equality expression.
     */
    private Expr expression()
    {
        return equality();
    }

    /*
     * Returns a simple expression or a comparison between two expressions.
     * These expressions are compared using "!=" or "==".
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
     * 
     */
    private ParseError error(Token token, String message)
    {
        Lox.error(token, message);

        return new ParseError();
    }

    /*
     * 
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
     * 
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
     * 
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
     * 
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
     * 
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
    }
}
