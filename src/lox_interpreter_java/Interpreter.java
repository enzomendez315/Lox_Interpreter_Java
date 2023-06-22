package lox_interpreter_java;

/*
 * This class is used to evaluate expressions and produce values 
 * using the syntax trees created by the parser.
 */
public class Interpreter implements Expr.Visitor<Object>
{

    private Object evaluate(Expr expr)
    {
        return expr.accept(this);
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) 
    {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type)
        {
            case GREATER:
                return (double)left > (double)right;
            case GREATER_EQUAL:
                return (double)left >= (double)right;
            case LESS:
                return (double)left < (double)right;
            case LESS_EQUAL:
                return (double)left <= (double)right;
            case MINUS:
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double)left + (double)right;

                if (left instanceof String && right instanceof String)
                    return (String)left + (String)right;

                break;
            case SLASH:
                return (double)left / (double)right;
            case STAR:
                return (double)left * (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }

        return null;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) 
    {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) 
    {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) 
    {
        Object right = evaluate(expr.right);

        switch (expr.operator.type)
        {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                return -(double)right;
        }

        return null;
    }

    /*
     * Returns false if the object is false or nil. 
     * Returns true for any other object.
     */
    private boolean isTruthy(Object object)
    {
        if (object == null)
            return false;
        
        if (object instanceof Boolean)
            return (boolean)object;

        return true;
    }

    /*
     * Checks if two objects are equal.
     */
    private boolean isEqual(Object a, Object b)
    {
        if (a == null && b == null)
            return true;
        
        if (a == null)
            return false;

        return a.equals(b);
    }
}