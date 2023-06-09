package lox_interpreter_java;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/*
 * This class is used to visit every node in a syntax tree to 
 * optimize variable resolution.
 */
public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void>
{
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;

    /*
     * Constructs a Resolver object.
     */
    public Resolver(Interpreter interpreter)
    {
        this.interpreter = interpreter;
    }

    private enum FunctionType
    {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    private enum ClassType
    {
        NONE,
        CLASS,
        SUBCLASS
    }

    private ClassType currentClass = ClassType.NONE;

    /*
     * Begins a new scope, traverses into the statements inside 
     * the block, and then discards the scope.
     */
    @Override
    public Void visitBlockStmt(Stmt.Block stmt)
    {
        beginScope();
        resolve(stmt.statements);
        endScope();

        return null;
    }

    /*
     * Declares and defines a class using its name. 
     * Then it resolves each one of the methods in 
     * the class body.
     */
    @Override
    public Void visitClassStmt(Stmt.Class stmt)
    {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        
        declare(stmt.name);
        define(stmt.name);

        if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme))
            Lox.error(stmt.superclass.name, "A class can't inherit from itself.");

        if (stmt.superclass != null)
        {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }

        if (stmt.superclass != null)
        {
            beginScope();
            scopes.peek().put("super", true);
        }

        beginScope();
        scopes.peek().put("this", true);

        for (Stmt.Function method : stmt.methods)
        {
            FunctionType declaration = FunctionType.METHOD;

            if (method.name.lexeme.equals("init"))
                declaration = FunctionType.INITIALIZER;

            resolveFunction(method, declaration);
        }

        endScope();

        if (stmt.superclass != null)
            endScope();

        currentClass = enclosingClass;

        return null;
    }

    /*
     * Resolves an expression statement.
     */
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt)
    {
        resolve(stmt.expression);

        return null;
    }

    /*
     * Declares and defines the name of the function in the 
     * current scope. Then resolves the function itself into 
     * an inner function scope.
     */
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt)
    {
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);

        return null;
    }

    /*
     * Resolves the condition and both branches of an 
     * 'if' statement.
     */
    @Override
    public Void visitIfStmt(Stmt.If stmt)
    {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);

        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);

        return null;
    }

    /*
     * Resolves a 'print' statement.
     */
    @Override
    public Void visitPrintStmt(Stmt.Print stmt)
    {
        resolve(stmt.expression);

        return null;
    }

    /*
     * Resolves the variable's value of a 
     * 'return' statement.
     */
    @Override
    public Void visitReturnStmt(Stmt.Return stmt)
    {
        if (currentFunction == FunctionType.NONE)
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        
        if (stmt.value != null)
        {
            if (currentFunction == FunctionType.INITIALIZER)
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            
            resolve(stmt.value);
        }

        return null;
    }

    /*
     * Declares and defines a variable statement.
     */
    @Override
    public Void visitVarStmt(Stmt.Var stmt)
    {
        declare(stmt.name);

        if (stmt.initializer != null)
            resolve(stmt.initializer);

        define(stmt.name);

        return null;
    }

    /*
     * Resolves the condition and body of a 
     * 'while' statement.
     */
    @Override
    public Void visitWhileStmt(Stmt.While stmt)
    {
        resolve(stmt.condition);
        resolve(stmt.body);

        return null;
    }

    /*
     * Checks for references to other variables and 
     * resolves the variable from the expression.
     */
    @Override
    public Void visitAssignExpr(Expr.Assign expr)
    {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        
        return null;
    }

    /*
     * Resolves both operands of a binary expression.
     */
    @Override
    public Void visitBinaryExpr(Expr.Binary expr)
    {
        resolve(expr.left);
        resolve(expr.right);

        return null;
    }

    /*
     * Resolves all the arguments of a call.
     */
    @Override
    public Void visitCallExpr(Expr.Call expr)
    {
        resolve(expr.callee);

        for (Expr argument : expr.arguments)
        {
            resolve(argument);
        }

        return null;
    }

    /*
     * Recurses into the expression to the left of the dot.
     */
    @Override
    public Void visitGetExpr(Expr.Get expr)
    {
        resolve(expr.object);

        return null;
    }

    /*
     * Resolves a grouping expression.
     */
    @Override
    public Void visitGroupingExpr(Expr.Grouping expr)
    {
        resolve(expr.expression);

        return null;
    }

    /*
     * Resolves a literal expression.
     */
    @Override
    public Void visitLiteralExpr(Expr.Literal expr)
    {
        return null;
    }

    /*
     * Resolves both operands of a logical expression.
     */
    @Override
    public Void visitLogicalExpr(Expr.Logical expr)
    {
        resolve(expr.left);
        resolve(expr.right);

        return null;
    }

    /*
     * Recurses into the two subexpresssions for 
     * the object whose property is being set, and 
     * for the value it's being set to.
     */
    @Override
    public Void visitSetExpr(Expr.Set expr)
    {
        resolve(expr.value);
        resolve(expr.object);

        return null;
    }

    /*
     * Resolves a 'super' expression.
     */
    @Override
    public Void visitSuperExpr(Expr.Super expr)
    {
        if (currentClass == ClassType.NONE)
            Lox.error(expr.keyword, "Can't use 'super' outside of a class.");
        else if (currentClass != ClassType.SUBCLASS)
            Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.");
        
        resolveLocal(expr, expr.keyword);

        return null;
    }

    /*
     * Resolves a 'this' expression.
     */
    @Override
    public Void visitThisExpr(Expr.This expr)
    {
        if (currentClass == ClassType.NONE)
        {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");

            return null;
        }
        
        resolveLocal(expr, expr.keyword);

        return null;
    }

    /*
     * Resolves the operand of a unary expression.
     */
    @Override
    public Void visitUnaryExpr(Expr.Unary expr)
    {
        resolve(expr.right);

        return null;
    }

    /*
     * Checks if the variable was declared but not yet defined. 
     * If so, it reports the error. Resolves the variable otherwise.
     */
    @Override
    public Void visitVariableExpr(Expr.Variable expr)
    {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE)
            Lox.error(expr.name, "Can't read local variable in its own initializer.");

        resolveLocal(expr, expr.name);

        return null;
    }

    /*
     * Takes a list of statements and resolves each one.
     */
    public void resolve(List<Stmt> statements)
    {
        for (Stmt statement : statements)
        {
            resolve(statement);
        }
    }

    /*
     * Applies the Visitor pattern to the given 
     * syntax tree node.
     */
    private void resolve(Stmt stmt)
    {
        stmt.accept(this);
    }

    /*
     * Applies the Visitor pattern to the given 
     * syntax tree node.
     */
    private void resolve(Expr expr)
    {
        expr.accept(this);
    }

    /*
     * Creates a new scope for the body of the function 
     * and binds variables for each of the parameters. Then 
     * it resolves the body in that scope before removing it.
     */
    private void resolveFunction(Stmt.Function function, FunctionType type)
    {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;

        beginScope();

        for (Token param : function.params)
        {
            declare(param);
            define(param);
        }

        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    /*
     * Adds a new scope to the stack.
     */
    private void beginScope()
    {
        scopes.push(new HashMap<String, Boolean>());
    }

    /*
     * Removes the innermost scope.
     */
    private void endScope()
    {
        scopes.pop();
    }

    /*
     * Adds a variable to the innermost scope so that it 
     * shadows any outer one. Then it binds the variable 
     * to 'false' to represent that the variable's initializer 
     * hasn't been resolved.
     */
    private void declare(Token name)
    {
        if (scopes.isEmpty())
            return;

        Map<String, Boolean> scope = scopes.peek();

        if (scope.containsKey(name.lexeme))
            Lox.error(name, "Already a variable with this name in this scope.");

        scope.put(name.lexeme, false);
    }

    /*
     * Sets the variable's value in the scope map to true 
     * to mark it as fully initialized and ready to use.
     */
    private void define(Token name)
    {
        if (scopes.isEmpty())
            return;
        
        scopes.peek().put(name.lexeme, true);
    }

    /*
     * Tries to resolve a variable by looking at the innermost 
     * scope and then going outwards. If it finds the variable, 
     * it resolves it. Otherwise, it is an unresolved global 
     * variable.
     */
    private void resolveLocal(Expr expr, Token name)
    {
        for (int i = scopes.size() - 1; i >= 0; i--)
        {
            if (scopes.get(i).containsKey(name.lexeme))
            {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }
}
