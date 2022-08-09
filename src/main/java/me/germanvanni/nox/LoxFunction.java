package me.germanvanni.nox;

import java.util.List;

public class LoxFunction implements LoxCallable{

    private final Stmt.Function declaration;
    LoxFunction(Stmt.Function declaration){
        this.declaration = declaration;
    }
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment enviroment = new Environment(interpreter.globals);
        for(int i = 0; i < declaration.params.size(); i++){
            enviroment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        interpreter.executeBlock(declaration.body, enviroment);
        return null;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
