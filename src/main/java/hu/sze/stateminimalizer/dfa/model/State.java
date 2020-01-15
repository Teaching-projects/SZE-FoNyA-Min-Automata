package hu.sze.stateminimalizer.dfa.model;

import java.util.HashMap;
import java.util.Map;

public class State {

    private int id;

    private String name;

    private Map<String, State> transitions = new HashMap<>();

    public State(int id) {
        this.id = id;
    }

    public State copy(){
        State state = new State(id);
        state.name =name;
        state.transitions.putAll(transitions);
        return state;
    }

    @Override
    public String toString() {
        return "state: "+id+"("+name+") event set:{"+
                transitions.keySet().toString()+"}";
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) || (obj != null && this.toString().equals(obj.toString()));
    }

    @Override
    public int hashCode() {
        return toString().hashCode()+id;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, State> getTransitions() {
        return transitions;
    }

    public void setName(String name) {
        this.name = name;
    }
}
