package lox_interpreter_java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This class is used to evaluate expressions and produce values 
 * using the syntax trees created by the parser.
 */
public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>
{
    protected final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    /*
     * Constructs an Interpreter object and defines its 
     * native functions.
     */
    public Interpreter()
    {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity()
            {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments)
            {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString()
            {
                return "<native fn>";
            }
        });
    }

    /*
     * Takes in a series of statements and evaluates them.
     * If a runtime error is thrown, it is caught and dealt with.
     */
    public void interpret(List<Stmt> statements)
    {
        try
        {
            for (Stmt statement : statements)
            {
                execute(statement);
            }
        } 
        catch (RuntimeError error)
        {
            Lox.runtimeError(error);
        }
    }

    /*
     * Evaluates a binary expression and returns the result.
     */
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) 
    {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type)
        {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                if (left instanceof Double && right instanceof Double)
                    return (double)left + (double)right;

                if (left instanceof String && right instanceof String)
                    return (String)left + (String)right;

                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }

        return null;
    }

    /*
     * Evaluates expression for callee and then for each of 
     * the arguments in order. Then it calls the function of 
     * the callee.
     */
    @Override
    public Object visitCallExpr(Expr.Call expr)
    {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments)
        {
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable))
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");

        LoxCallable function = (LoxCallable)callee;

        if (arguments.size() != function.arity())
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        
        return function.call(this, arguments);
    }

    /*
     * Evaluates the expression whose property is being accessed.
     * If the object is not an instance of a class, it throws 
     * an error.
     */
    @Override
    public Object visitGetExpr(Expr.Get expr)
    {
        Object object = evaluate(expr.object);

        if (object instanceof LoxInstance)
            return ((LoxInstance)object).get(expr.name);

        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    /*
     * Evaluates a grouping expression and returns the result.
     */
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) 
    {
        return evaluate(expr.expression);
    }

    /*
     * Evaluates a literal expression and returns the result.
     */
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) 
    {
        return expr.value;
    }

    /*
     * Evaluates left operand first to see if it meets conditions 
     * for 'and'/'or' statements. Then evaluates the right operand.
     */
    @Override
    public Object visitLogicalExpr(Expr.Logical expr)
    {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR)
        {
            if (isTruthy(left))
                return left;
        }
        else
        {
            if (!isTruthy(left))
                return left;
        }

        return evaluate(expr.right);
    }

    /*
     * Evaluates the object whose property is being set 
     * and checks if it is an instance of a class. If it 
     * isn't, it throws an error. Otherwise, it evaluates 
     * the value being set and stores it on the instance.
     */
    @Override
    public Object visitSetExpr(Expr.Set expr)
    {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance))
            throw new RuntimeError(expr.name, "Only instances have fields.");

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);

        return value;
    }

    /*
     * Looks up the superclass of a class in the proper 
     * environment and binds 'this' to the object the 
     * method is accessed from.
     */
    @Override
    public Object visitSuperExpr(Expr.Super expr)
    {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass)environment.getAt(distance, "super");
        LoxInstance object = (LoxInstance)environment.getAt(distance - 1, "this");
        LoxFunction method = superclass.findMethod(expr.method.lexeme);

        if (method == null)
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");

        return method.bind(object);
    }

    /*
     * Evaluates a 'this' expression.
     */
    @Override
    public Object visitThisExpr(Expr.This expr)
    {
        return lookUpVariable(expr.keyword, expr);
    }

    /*
     * Evaluates a unary expression and returns the result.
     */
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) 
    {
        Object right = evaluate(expr.right);

        switch (expr.operator.type)
        {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        return null;
    }

    /*
     * Evaluates a variable expression and returns the result.
     */
    @Override
    public Object visitVariableExpr(Expr.Variable expr)
    {
        return lookUpVariable(expr.name, expr);
    }

    /*
     * Looks up the distance of the resolved variable and returns 
     * the variable itself.
     */
    private Object lookUpVariable(Token name, Expr expr)
    {
        Integer distance = locals.get(expr);

        if (distance != null)
            return environment.getAt(distance, name.lexeme);
        else
            return globals.get(name);
    }

    /*
     * Evaluates an expression.
     */
    private Object evaluate(Expr expr)
    {
        return expr.accept(this);
    }

    /*
     * Executes a statement.
     */
    private void execute(Stmt stmt)
    {
        stmt.accept(this);
    }

    /*
     * Stores the resolution information (the expression 
     * and the number of environments between the current 
     * one and the enclosing where the variable's value 
     * is found).
     */
    public void resolve(Expr expr, int depth)
    {
        locals.put(expr, depth);
    }

    /*
     * Executes each statement in a block and 
     * sets a new environment for the scope.
     */
    public void executeBlock(List<Stmt> statements, Environment environment)
    {
        Environment previous = this.environment;
        try
        {
            this.environment = environment;

            for (Stmt statement : statements)
            {
                execute(statement);
            }
        }
        finally
        {
            this.environment = previous;
        }
    }

    /*
     * Evaluates an entire block or scope.
     */
    @Override
    public Void visitBlockStmt(Stmt.Block stmt)
    {
        executeBlock(stmt.statements, new Environment(environment));

        return null;
    }

    /*
     * Declares the class's name in the current environment and 
     * binds it to a class object. If the class has a superclass 
     * expression, it evaluates it.
     */
    @Override
    public Void visitClassStmt(Stmt.Class stmt)
    {
        Object superclass = null;
        if (stmt.superclass != null)
        {
            superclass = evaluate(stmt.superclass);

            if (!(superclass instanceof LoxClass))
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
        }
        
        environment.define(stmt.name.lexeme, null);

        if (stmt.superclass != null)
        {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        
        for (Stmt.Function method : stmt.methods)
        {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass, methods);

        if (superclass != null)
            environment = environment.enclosing;

        environment.assign(stmt.name, klass);

        return null;
    }

    /*
     * Evaluates an expression statement and returns the result.
     */
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt)
    {
        evaluate(stmt.expression);

        return null;
    }

    /*
     * Evaluates a function declaration and binds the resulting 
     * object to a new variable.
     */
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt)
    {
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);

        return null;
    }

    /*
     * Evaluates a condition and executes the 'then' 
     * branch if true. Executes the 'else' branch if false.
     */
    @Override
    public Void visitIfStmt(Stmt.If stmt)
    {
        if (isTruthy(evaluate(stmt.condition)))
            execute(stmt.thenBranch);
        else if (stmt.elseBranch != null)
            execute(stmt.elseBranch);

        return null;
    }

    /*
     * Prints an expression statement.
     */
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) 
    {
       Object value = evaluate(stmt.expression);
       System.out.println(stringify(value));

       return null;
    }

    /*
     * Evaluates the return value or throws an exception 
     * if there isn't one.
     */
    @Override
    public Void visitReturnStmt(Stmt.Return stmt)
    {
        Object value = null;
        if (stmt.value != null)
            value = evaluate(stmt.value);

        throw new Return(value);
    }

    /*
     * Evaluates a variable statement.
     */
    @Override
    public Void visitVarStmt(Stmt.Var stmt)
    {
        Object value = null;
        if (stmt.initializer != null)
            value = evaluate(stmt.initializer);

        environment.define(stmt.name.lexeme, value);

        return null;
    }

    /*
     * Executes a while statement.
     */
    @Override
    public Void visitWhileStmt(Stmt.While stmt)
    {
        while (isTruthy(evaluate(stmt.condition)))
        {
            execute(stmt.body);
        }

        return null;
    }

    /*
     * Evaluates an assignment expression.
     */
    @Override
    public Object visitAssignExpr(Expr.Assign expr)
    {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);

        if (distance != null)
            environment.assignAt(distance, expr.name, value);
        else
            globals.assign(expr.name, value);

        return value;
    }

    /*
     * Checks that the operand is a number on which the operator
     * can be used.
     */
    private void checkNumberOperand(Token operator, Object operand)
    {
        if (operand instanceof Double)
            return;
        
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    /*
     * Checks that the operands are numbers on which the operator
     * can be used.
     */
    private void checkNumberOperands(Token operator, Object left, Object right)
    {
        if (left instanceof Double && right instanceof Double)
            return;
        
        throw new RuntimeError(operator, "Operands must be numbers.");
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

    /*
     * Returns the string representation of the object.
     */
    private String stringify(Object object)
    {
        if (object == null)
            return "nil";

        if (object instanceof Double)
        {
            String text = object.toString();
            if (text.endsWith(".0"))
                text = text.substring(0, text.length() - 2);

            return text;
        }

        return object.toString();
    }
}
