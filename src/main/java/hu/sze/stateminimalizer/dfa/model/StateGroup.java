package hu.sze.stateminimalizer.dfa.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StateGroup {
    public final int id;
    public final List<State> states = new ArrayList<>();

    public StateGroup(int id) {
        this.id = id;
    }

    public StateGroup(int id, State... states){
        this(id);
        this.states.addAll(Arrays.asList(states));
    }

    @Override
    public String toString() {
        return "state group: "+id+" states :" + states.stream().map(State::getName).collect(Collectors.joining(", ") );
    }
}
