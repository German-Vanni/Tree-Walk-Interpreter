package me.germanvanni.jlox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void>{

    Environment globals = new Environment();
    private final Map<Expr, Integer> locals = new HashMap<>();
    private Environment environment = globals;

    Interpreter(){
        globals.define("clock", new LoxCallable() {
            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public int arity() {
                return 0;
            }
        });
    }

    void interpret(List<Stmt> statements){
        try{
            for(Stmt statement : statements){
                execute(statement);
            }
        } catch ( RuntimeError e){
            Lox.runtimeError(e);
        }
    }

    private void execute(Stmt statement){
        statement.accept(this);
    }

    void resolve(Expr expr, int depth){
        locals.put(expr, depth);
    }

    void executeBlock(List<Stmt> statements, Environment environment){
        Environment previous = this.environment;
        try{
            this.environment = environment;
            for(Stmt stmt : statements){
                execute(stmt);
            }

        }finally {
            this.environment = previous;
        }

    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if(stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if(isTruthy(evaluate(stmt.condition))){
            execute(stmt.thenBranch);
        } else if(stmt.elseBranch != null){
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object calle = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for(Expr argument:expr.arguments){
            arguments.add(evaluate(argument));
        }

        if(!(calle instanceof LoxCallable)){
            throw new RuntimeError(expr.paren, "Can only call functions and classes");
        }

        LoxCallable function = (LoxCallable) calle;

        if(arguments.size() != function.arity()){
            throw new RuntimeError(expr.paren, "Expected " + function.arity()
                    + " arguments but got " + arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if(object instanceof LoxInstance){
            return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name, "Only instances have properties");
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if(!(object instanceof LoxInstance)){
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superClass = (LoxClass)environment.getAt(distance, "super");

        LoxInstance object = (LoxInstance) environment.getAt(distance - 1, "this");

        LoxFunction method = superClass.findMethod(expr.method.lexeme);

        if (method == null){
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt){
        Object value = null;
        if(stmt.initializer != null){
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {

        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr){
        Integer distance = locals.get(expr);
        if(distance != null){
            return environment.getAt(distance, name.lexeme);
        }
        else{
            return globals.get(name);
        }
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch(expr.operator.type){
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case PLUS:
                //we allow implicit conversion of numbers to strings
                //("2" + 2 == "22" && 2 + "2" == "22") is true
                if(left instanceof Double){

                    if(right instanceof Double) {
                        return (double) left + (double) right;
                    }else if (right instanceof String){
                        String strLeft =  Double.toString((double)left);
                        if(strLeft.endsWith(".0")){
                            strLeft = strLeft.substring(0, strLeft.length() - 2);
                        }
                        return strLeft + (String)right;
                    }
                }
                if(left instanceof String){
                    if(right instanceof String){
                        return (String)left + (String)right;
                    } else if (right instanceof Double){
                        String strRight =  Double.toString((double)right);
                        if(strRight.endsWith(".0")){
                            strRight = strRight.substring(0, strRight.length() - 2);
                        }
                        return (String)left + strRight;
                    }
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                double divisor = (double)right;
                if(divisor == 0) throw new RuntimeError(expr.operator, "Can't divide by 0!");
                return (double)left / (double)right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
        }

        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superClass = null;
        if(stmt.superclass != null){
            superClass = evaluate(stmt.superclass);
            if(!(superClass instanceof LoxClass)){
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }
        environment.define(stmt.name.lexeme, null);

        if(stmt.superclass != null){
            environment = new Environment(environment);
            environment.define("super", superClass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for(Stmt.Function method : stmt.methods){
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("ctor"));
            methods.put(method.name.lexeme, function);
        }
        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superClass, methods);

        if(superClass != null){
            environment = environment.enclosing;
        }

        environment.assign(stmt.name, klass);
        return null;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr){
        Object value = expr.value;

        Integer distance = locals.get(expr);
        if(distance != null){
            environment.assignAt(distance, expr.name, value);
        } else{
            globals.assign(expr.name, value);
        }

        return value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        switch(expr.operator.type){
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;

        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while(isTruthy(evaluate(stmt.condition))){
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if(expr.operator.type == TokenType.OR){
            if(isTruthy(left)) return left;
        } else{
            if(!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    private Object evaluate(Expr expr){
        return expr.accept(this);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    private boolean isTruthy(Object object){
        //"null" and "false" are false, everything else is true:
        if(object == null) return false;

        if(object instanceof Boolean) return (boolean)object;

        return true;
    }

    private boolean isEqual(Object a, Object b){
        if(a == null && b == null) return true;
        if(a == null) return false;
        return a.equals(b);
    }
    private void checkNumberOperand(Token operator, Object operand){
        if(operand instanceof Double) return;
        throw new RuntimeError(operator, "Operator must be a number");
    }
    private void checkNumberOperands(Token operator, Object left, Object right){
        if(left instanceof Double && right instanceof Double) return;
        throw new RuntimeError(operator, "Operands must be numbers");
    }

    private String stringify (Object object){
        if(object == null) return "null";

        if(object instanceof Double){
            String t = object.toString();
            if(t.endsWith(".0")){
                t = t.substring(0, t.length() - 2);
            }
            return t;
        }

        return object.toString();
    }
}
