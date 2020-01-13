package hu.sze.stateminimalizer.dfa;

import hu.sze.stateminimalizer.dfa.model.DFA;
import hu.sze.stateminimalizer.dfa.model.State;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.plaf.basic.BasicScrollPaneUI;
import java.util.*;
import java.util.stream.Collectors;

public class Minimizer {

    private final DFA initialDfa;
    private DFA reducedDfa;
    private ArrayList<Set<State>> currentStateGroups;
    private Map<State, String> stateActiveEventSetMap;
    List<Pair<State, State>> conflicts = new ArrayList<>();
    Map<Pair<String, State>, Integer> stateGroupTransitionMap = new HashMap<>();



    public Minimizer(DFA dfa) {
        this.initialDfa = dfa;
    }

    public DFA minimize(){
        return new DFA();
    }


    public DFA reduceByUnreachableStates(){
        List<State> reachableStates = extractReachableStates(initialDfa);
        this.reducedDfa = buildReducedDfa(reachableStates);
        return reducedDfa;
    }

    private DFA buildReducedDfa(List<State> reachableStates) {
        DFA dfa = new DFA();

        dfa.getStates().addAll(reachableStates.stream().map(State::copy).collect(Collectors.toList()));
        List<Integer> reachableStateIds = reachableStates.stream().map(state -> state.getId()).collect(Collectors.toList());
        List<Integer> finalStateIds = initialDfa.getFinalStateIds().stream().filter(reachableStateIds::contains).collect(Collectors.toList());

        dfa.getFinalStateIds().addAll(finalStateIds);
        dfa.getInputSymbols().addAll(initialDfa.getInputSymbols());
        return dfa;
    }

    public void groupByIsFinalState(){
        Pair<Set<State>, Set<State>> pair = collectFinalAndNonFinalStates(reducedDfa);
        this.currentStateGroups = new ArrayList<>();
        this.currentStateGroups.add(pair.getLeft());
        this.currentStateGroups.add(pair.getRight());
    }

    /**
     *
     * @return state-event set matching
     */
    public void groupByEventSet(){
        Map<State, String> stateStringMap = new HashMap<>();

        ArrayList<Set<State>> newStateGroups = new ArrayList<>();
        currentStateGroups.forEach(stateGroup -> {
            Map<String, Set<State>> eventSetMap = groupByEventSet(stateGroup);
            eventSetMap.forEach((eventSet, states) -> {
                newStateGroups.add(states);
                states.forEach(state -> stateStringMap.put(state, eventSet));
            });
        });
        currentStateGroups = newStateGroups;
        stateActiveEventSetMap = stateStringMap;
    }

    private Map<String, Set<State>> groupByEventSet(Set<State> states){
        Map<String, Set<State>> eventSet = new HashMap<>();
        for(State state : states){
            StringJoiner sb = new StringJoiner(",");
            state.getTransitions().keySet().forEach(sb::add);
            String key = sb.toString();
            if(eventSet.containsKey(key)){
                eventSet.get(key).add(state);
            } else {
                Set<State> stateGroup = new LinkedHashSet<>();
                stateGroup.add(state);
                eventSet.put(key, stateGroup);
            }
        }
        System.out.println("Event set alapjan tovabb bontasok: ");
        System.out.println(eventSet);
        return eventSet;
    }

    private Pair<Set<State>, Set<State>> collectFinalAndNonFinalStates(DFA dfa){
        Set<State> finalStates = new LinkedHashSet<>(dfa.getFinalStates());
        Set<State> nonFinalStates = dfa.getStates().stream().filter(state -> !finalStates.contains(state)).collect(Collectors.toCollection(LinkedHashSet::new));
        System.out.println("Piros x-ek alapja:");
        System.out.println("Vegallapot(ok):" + finalStates);
        System.out.println("Nem vegallapotok: "+ nonFinalStates);
        return Pair.of(finalStates, nonFinalStates);
    }

    /**
     * Removes all the states that are not visible/reachable
     * @param dfa
     * @return   reachable states
     */
    private List<State> extractReachableStates(DFA dfa){
        List<State> reachableStates = new ArrayList<>();
        State initialState = dfa.getStates().stream().filter(state -> state.getId() == DFA.INITIAL_STATE_ID).findAny().orElseGet(null);
        reachableStates.add(initialState);
        List<State> queue = new LinkedList<>();
        queue.add(initialState);
        while (!queue.isEmpty()){
            State state = queue.remove(0);
            state.getTransitions().values().forEach(entry -> {
                if(!reachableStates.contains(entry)){
                    queue.add(entry);
                    reachableStates.add(entry);
                }
            });
        }
        return reachableStates;
    }

    public int stateGorupsSize(){
        return currentStateGroups.size();
    }

    public boolean isSameActiveEventSet(State rowState, State columnState) {
        return Objects.equals(getActiveEventSet(rowState), getActiveEventSet(columnState));
    }

    public String getActiveEventSet(State state){
        return stateActiveEventSetMap.get(state);
    }

    public boolean mergeableByIsFinal(State stateA, State stateB){
        boolean isFinalA = reducedDfa.getFinalStateIds().contains(stateA.getId());
        boolean isFinalB = reducedDfa.getFinalStateIds().contains(stateB.getId());
        return (isFinalA && isFinalB) || (!isFinalA && !isFinalB);
    }

    public boolean isMergeableByTargetStates(State stateA, State stateB){
        if(conflicts.contains(Pair.of(stateA, stateB)) || conflicts.contains(Pair.of(stateB, stateA))){
            return false;
        }
        int initialConflicts = conflicts.size();
        //they are in the same event set group
        stateA.getTransitions().forEach((inputSymbol, targetStateA) -> {
            State targetStateB = stateB.getTransitions().get(inputSymbol);
            if(targetStateA != targetStateB){
                int targetGroupA = stateGroupTransitionMap.getOrDefault(Pair.of(inputSymbol, stateA), -1);
                int targetGroupB = stateGroupTransitionMap.getOrDefault(Pair.of(inputSymbol, stateA), -1);
                for(int i = 0; i<currentStateGroups.size(); i++){
                    Set<State> stateGroup = currentStateGroups.get(i);
                    if(targetGroupA < 0 && stateGroup.contains(targetStateA)){
                        targetGroupA = i;
                    } else if(targetGroupB < 0 && stateGroup.contains(targetStateB)){
                        targetGroupB = i;
                    }
                    if(targetGroupA >= 0 && targetGroupB >= 0){
                        break;
                    }
                }
                if(targetGroupA != targetGroupB){
                    conflicts.add(Pair.of(stateA,stateB));
                }
                stateGroupTransitionMap.put(Pair.of(inputSymbol, stateA), targetGroupA);
                stateGroupTransitionMap.put(Pair.of(inputSymbol, stateB), targetGroupB);
            }
        } );
        return initialConflicts == conflicts.size();
    }
}
