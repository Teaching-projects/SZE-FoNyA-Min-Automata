package hu.sze.stateminimalizer.dfa;

import hu.sze.stateminimalizer.dfa.model.DFA;
import hu.sze.stateminimalizer.dfa.model.State;
import hu.sze.stateminimalizer.dfa.model.StateGroup;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Minimizer {

    private ArrayList<Pair<Integer, Integer>> redMarked = new ArrayList<>();
    private ArrayList<Pair<Integer, Integer>> blueMarked = new ArrayList<>();
    private int currentStateGroupIndex = 0;
    private ArrayList<Pair<Integer, Integer>> marked;
    private Map<String, StateGroup> finalStateEventSetMap;
    private Map<String, StateGroup> nonFinalStateEventSetMap;
    private Map<Integer, State> statesByIds;
    private DFA dfa;
    private List<Pair<Integer, Integer>> pairs;
    private List<StateGroup> minimalizedGroups;

    public DFA minimize(DFA initialDfa){
        dfa = removeUnreachableStates(initialDfa);
        statesByIds = dfa.getStates().stream().collect(Collectors.toMap(State::getId, Function.identity()));
        marked = new ArrayList<>();

        markByIsFinalState();
        pairs = createStatePairs();

        setupStateGroupsByEventSets();
        groupByTargetStateGroups();

        minimalizedGroups = getMinimizedStateGroups();

        System.out.println("final groups: ");
        minimalizedGroups.forEach(stateGroup -> System.out.println(stateGroup.toString()));

        return dfa;
    }

    private List<StateGroup> getMinimizedStateGroups() {
        List<StateGroup> minimizedGroups = new ArrayList<>();

        Set<Integer> stateIdsInGroup = new HashSet<>();
        for(int rowIndex = 0; rowIndex< dfa.getStates().size(); rowIndex++){
            State rowState = dfa.getStates().get(rowIndex);
            boolean hasPair = checkRowMatchingGroups(rowState, rowIndex, stateIdsInGroup, minimizedGroups);
            if(!stateIdsInGroup.contains(rowState.getId()) && !hasPair){
                StateGroup newStateGroup = new StateGroup(
                        minimizedGroups.size()+1);
                newStateGroup.states.add(rowState);
                minimizedGroups.add(newStateGroup);
                stateIdsInGroup.add(rowState.getId());
            }
        }
        return minimizedGroups;
    }

    private boolean checkRowMatchingGroups(State rowState, int rowIndex, Set<Integer> stateIdsInGroup, List<StateGroup> minimizedGroups ){
        boolean hasPair = false;
        for(int columnIndex = rowIndex+1; columnIndex < dfa.getStates().size(); columnIndex++ ){
            State columnState = dfa.getStates().get(columnIndex);
            if(!stateIdsInGroup.contains(columnState.getId()) && !isMarked(rowState, columnState)){
                Optional<StateGroup> group = minimizedGroups.stream()
                        .filter(stateGroup -> stateGroup.states.contains(rowState)).findFirst();
                if(group.isPresent()){
                    group.get().states.add(columnState);
                    stateIdsInGroup.add(columnState.getId());
                } else { //create new Group
                    StateGroup newStateGroup = new StateGroup(minimizedGroups.size()+1);
                    newStateGroup.states.add(rowState);
                    newStateGroup.states.add(columnState);
                    stateIdsInGroup.add(rowState.getId());
                    stateIdsInGroup.add(columnState.getId());
                    minimizedGroups.add(newStateGroup);
                }
                hasPair = true;
            } //else nothing to do here
        }
        return hasPair;
    }

    private void groupByTargetStateGroups() {
        //left is the owner, right is a list of dependent pairs
        Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs = new HashMap<>();
        pairs.stream().filter(statePair -> !isMarked(statePair))
                .forEachOrdered(statePair -> {
                    checkPairTransitions(statePair, dependentPairs);
                });
        System.out.println("Dependent map: " + dependentPairs.toString());
    }

    private void markByIsFinalState() {
        dfa.getStates().stream().filter(state -> !dfa.getFinalStateIds().contains(state.getId()))
                .forEach(nonFinalState -> {
                    dfa.getFinalStateIds().forEach(finalStateId -> {
                        marked.add(Pair.of(nonFinalState.getId(), finalStateId));
                        marked.add(Pair.of(finalStateId, nonFinalState.getId()));
                    } );
                });
        redMarked.addAll(marked);
    }

    private void setupStateGroupsByEventSets() {
        StateGroup finalStates = createStateGroup();
        finalStates.states.addAll(dfa.getFinalStates());

        StateGroup nonFinalStates = createStateGroup();
        nonFinalStates.states.addAll(dfa.getStates().stream().filter(state -> !dfa.getFinalStateIds().contains( state.getId())).collect(Collectors.toList()));

        finalStateEventSetMap = createStateGroupsByEvent(finalStates.states);
        nonFinalStateEventSetMap = createStateGroupsByEvent(nonFinalStates.states);

        pairs.stream().filter(statePair -> !isMarked(statePair))
                .forEachOrdered(statePair -> {
                    int stateGroupIdA = findEventSetGroup(statePair.getLeft()).getValue().id;
                    int stateGroupIdB = findEventSetGroup(statePair.getRight()).getValue().id;
                    if(stateGroupIdA != stateGroupIdB){
                        blueMarked.add(statePair);
                    }
        });
        marked.addAll(blueMarked);
    }

    private boolean isMarked(Pair<Integer, Integer> statePair){
        State stateA = findStateById(statePair.getLeft());
        State stateB = findStateById(statePair.getRight());
        return isMarked(stateA, stateB);
    }

    private State findStateById(int stateId){
        return statesByIds.get(stateId);
    }

    private Map.Entry<String, StateGroup> findEventSetGroup(Integer stateId) {
        State state = dfa.getStates().stream().filter(dfaState -> dfaState.getId() == stateId).findFirst().get();
        Map<String, StateGroup> eventSetGroup = null;
        if(dfa.getFinalStateIds().contains(stateId)){
            eventSetGroup = finalStateEventSetMap;
        } else {
            eventSetGroup = nonFinalStateEventSetMap;
        }
        return eventSetGroup.entrySet().stream().filter(entry -> entry.getValue().states.contains(state)).findFirst().get();

    }

    private void checkPairTransitions(Pair<Integer, Integer> statePair, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs) {
        State stateA = findStateById(statePair.getLeft());
        State stateB = findStateById(statePair.getRight());
        if(stateA.getId() != stateB.getId()) {

            for (Map.Entry<String, State> entry : stateA.getTransitions().entrySet()) {
                String inputSymbol = entry.getKey();
                State targetStateA = entry.getValue();
                State targetStateB = stateB.getTransitions().get(inputSymbol);
                if(targetStateA.getId() != targetStateB.getId()) {
                    if (areInDifferentStateGroup(targetStateA, targetStateB)) {
                        mark(statePair, dependentPairs);
                    } else {
                        addAsDependent(statePair, dependentPairs, targetStateA, targetStateB);
                    }
                }
            }
        }
    }

    private void addAsDependent(Pair<Integer, Integer> statePair, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs, State targetStateA, State targetStateB) {
        dependentPairs.compute(Pair.of(targetStateA.getId(), targetStateB.getId()), (owner, dependentPairList) -> {
            List<Pair<Integer, Integer>> dependents = dependentPairList != null ? dependentPairList : new ArrayList<>();
            dependents.add(statePair);
            return dependents;
         });
    }

    private boolean areInDifferentStateGroup(State targetStateA, State targetStateB) {

        if((targetStateA == null && targetStateB!= null) || (targetStateB == null && targetStateA != null)){
            return true;
        }
        if(targetStateA == null){ //both null
            return false;
        } else if(isMarked(targetStateA, targetStateB)){
              return true;
            } else {
               StateGroup targetStateGroupA = findStateGroup(targetStateA);
               StateGroup targetStateGroupB = findStateGroup(targetStateB);
               return targetStateGroupA.id != targetStateGroupB.id;
        }
    }

    private void mark(Pair<Integer, Integer> statePair, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs){
        marked.add(statePair);
        if(dependentPairs.containsKey(statePair)){
            marked.addAll(getDependentsPairsRecursive(statePair, dependentPairs));
        }
    }

    private StateGroup findStateGroup(State state) {
        return finalStateEventSetMap.values().stream()
                .filter(stateGroup -> stateGroup.states.contains(state))
                .findAny()
                .orElseGet(() -> nonFinalStateEventSetMap.values().stream()
                        .filter(stateGroup -> stateGroup.states.contains(state))
                        .findAny().orElse(null));
        }

    private List<Pair<Integer, Integer>> getDependentsPairsRecursive(Pair<Integer, Integer> owner, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs){
        List<Pair<Integer, Integer>> pairs = new ArrayList<>();
        List<Pair<Integer, Integer>> ownerPair = dependentPairs.get(owner);
        if(ownerPair != null) {
            ownerPair.forEach(dependentPair -> {
                pairs.add(dependentPair);
                pairs.addAll(getDependentsPairsRecursive(dependentPair, dependentPairs));
            });
        }
        return pairs;
    }

    private StateGroup createStateGroup() {
        return new StateGroup(currentStateGroupIndex++);
    }

    public boolean isMarkedAsRed(State stateA, State stateB){
        return redMarked.contains(Pair.of(stateA.getId(), stateB.getId())) || redMarked.contains(Pair.of(stateB.getId(), stateA.getId()));
    }

    public boolean isMarkedAsBlue(State stateA, State stateB){
        return blueMarked.contains(Pair.of(stateA.getId(), stateB.getId())) || blueMarked.contains(Pair.of(stateB.getId(), stateA.getId()));
    }

    public boolean isMarked(State stateA, State stateB) {
        return marked.contains(Pair.of(stateA.getId(), stateB.getId())) || marked.contains(Pair.of(stateB.getId(), stateA.getId()));
    }

    public String getActiveEventSet(State state){
       return findEventSetGroup(state.getId()).getKey();
    }

    public DFA removeUnreachableStates(DFA initialDfa){
        List<State> reachableStates = extractReachableStates(initialDfa);
        return buildReducedDfa(reachableStates, initialDfa);
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
        reachableStates.sort(Comparator.comparingInt(State::getId));
        return reachableStates;
    }

    private DFA buildReducedDfa(List<State> reachableStates, DFA initialDfa) {
        DFA dfa = new DFA();

        dfa.getStates().addAll(reachableStates.stream().map(State::copy).collect(Collectors.toList()));
        List<Integer> reachableStateIds = reachableStates.stream().map(State::getId).collect(Collectors.toList());
        List<Integer> finalStateIds = initialDfa.getFinalStateIds().stream().filter(reachableStateIds::contains).collect(Collectors.toList());

        dfa.getFinalStateIds().addAll(finalStateIds);
        dfa.getInputSymbols().addAll(initialDfa.getInputSymbols());
        return dfa;
    }

    private Map<String, StateGroup> createStateGroupsByEvent(List<State> states){
        Map<String, StateGroup> eventSet = new HashMap<>();
        for(State state : states){
            StringJoiner sb = new StringJoiner(",");
            state.getTransitions().keySet().forEach(sb::add);
            String key = sb.toString();
            if(eventSet.containsKey(key)){
                eventSet.get(key).states.add(state);
            } else {
                StateGroup stateGroup = createStateGroup();
                stateGroup.states.add(state);
                eventSet.put(key, stateGroup);
            }
        }
        System.out.println("Event set alapjan tovabb bontasok: ");
        System.out.println(eventSet);
        return eventSet;
    }

    private List<Pair<Integer, Integer>> createStatePairs(){
        List<Pair<Integer, Integer>> pairs = new ArrayList<>();
        for(int rowIndex = 0; rowIndex< dfa.getStates().size(); rowIndex++){
            State rowState = dfa.getStates().get(rowIndex);
            for(int columnIndex = rowIndex+1; columnIndex < dfa.getStates().size(); columnIndex++ ){
                pairs.add(Pair.of(rowState.getId(), dfa.getStates().get(columnIndex).getId()));
            }
        }
        return pairs;
    }


}
