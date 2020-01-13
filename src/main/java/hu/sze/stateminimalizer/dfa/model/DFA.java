package hu.sze.stateminimalizer.dfa.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic Finite Automaton
 */
public class DFA {
    public static int INITIAL_STATE_ID = 0;

    private Set<String> inputSymbols = new LinkedHashSet<>();

    private List<State> states = new LinkedList<>();

    private Set<Integer> finalStateIds = new LinkedHashSet<>();

    public State getInitialState(){
      return states.stream().filter(state -> state.getId() == INITIAL_STATE_ID).findAny().orElseThrow(() -> new IllegalStateException("Unknown initialState"));
    }

    public List<State> getFinalStates(){
        return states.stream().filter(state -> finalStateIds.contains(state.getId())).collect(Collectors.toList());
    }

    public Set<String> getInputSymbols() {
        return inputSymbols;
    }

    public List<State> getStates() {
        return states;
    }

    public Set<Integer> getFinalStateIds() {
        return finalStateIds;
    }
}
