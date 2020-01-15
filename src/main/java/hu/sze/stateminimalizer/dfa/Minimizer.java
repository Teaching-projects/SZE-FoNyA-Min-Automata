package hu.sze.stateminimalizer.dfa;

import hu.sze.stateminimalizer.dfa.model.DFA;
import hu.sze.stateminimalizer.dfa.model.State;
import hu.sze.stateminimalizer.dfa.model.StateGroup;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Minimizer {

    private ArrayList<Pair<Integer, Integer>> redMarked = new ArrayList<>();
    private ArrayList<Pair<Integer, Integer>> blueMarked = new ArrayList<>();
    private int currentStateGroupIndex = 0;
    private ArrayList<Pair<Integer, Integer>> marked;
    private Map<String, StateGroup> finalStateEventSetMap;
    private Map<String, StateGroup> nonFinalStateEventSetMap;
    private DFA dfa;
    private List<Pair<Integer, Integer>> pairs;

    public DFA minimize(DFA initialDfa){
        dfa = reduceByUnreachableStates(initialDfa);
        marked = new ArrayList<>();

        dfa.getStates().stream().filter(state -> !dfa.getFinalStateIds().contains(state.getId()))
                .forEach(state -> {
                    dfa.getFinalStateIds().forEach(finalStateId -> marked.add(Pair.of(state.getId(), finalStateId)));
                });
        redMarked.addAll(marked);
        pairs = createStatePairs();

        setupStateGroupsByEventSets();

        Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs = new HashMap<>();
        pairs.stream().filter(statePair -> !marked.contains(statePair) && !marked.contains(Pair.of(statePair.getRight(), statePair.getLeft())))
                .forEachOrdered(statePair -> {
                    checkPair(statePair, dependentPairs);
                });
        List<StateGroup> minimalizedGroups = new ArrayList<>();

        for(int rowIndex = 0; rowIndex< dfa.getStates().size(); rowIndex++){
            State rowState = dfa.getStates().get(rowIndex);
            for(int columnIndex = rowIndex+1; columnIndex < dfa.getStates().size()-1; columnIndex++ ){
                State columnState = dfa.getStates().get(columnIndex);
               if(!isMarked(rowState, columnState)){
                   minimalizedGroups.stream()
                           .filter(stateGroup -> stateGroup.states.contains(rowState)).findFirst()
                           .ifPresentOrElse(stateGroup -> stateGroup.states.add(columnState),
                                   () -> {
                               StateGroup newStateGroup = createStateGroup();
                               newStateGroup.states.add(rowState);
                               newStateGroup.states.add(columnState);
                               minimalizedGroups.add(newStateGroup);
                   });

               }
            }
        }

        System.out.println("final groups: ");
        minimalizedGroups.forEach(stateGroup -> System.out.println(stateGroup.toString()));

        return dfa;
    }

    private void setupStateGroupsByEventSets() {
        StateGroup finalStates = createStateGroup();
        finalStates.states.addAll(dfa.getFinalStates());

        StateGroup nonFinalStates = createStateGroup();
        nonFinalStates.states.addAll(dfa.getStates().stream().filter(state -> !dfa.getFinalStateIds().contains( state.getId())).collect(Collectors.toList()));

        finalStateEventSetMap = createStateGroupsByEvent(finalStates.states);
        nonFinalStateEventSetMap = createStateGroupsByEvent(nonFinalStates.states);

        pairs.stream().filter(statePair -> !marked.contains(statePair)).forEachOrdered(statePair -> {
            int stateGroupIdA = findEventSetGroup(statePair.getLeft()).getValue().id;
            int stateGroupIdB = findEventSetGroup(statePair.getRight()).getValue().id;
            if(stateGroupIdA != stateGroupIdB){
                blueMarked.add(statePair);
            }
        });
        marked.addAll(blueMarked);
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

    private void checkPair(Pair<Integer, Integer> statePair, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs) {
        dfa.getStates().stream().filter(state -> state.getId()==statePair.getLeft()).forEach(stateA -> {
            for (Map.Entry<String, State> entry : stateA.getTransitions().entrySet()) {
                String inputSymbol = entry.getKey();
                State targetStateA = entry.getValue();
                State stateB = dfa.getStates().stream().filter(state -> state.getId() == statePair.getRight()).findFirst().get();
                State targetStateB = stateB.getTransitions().get(inputSymbol);
                if(isNeedToBeMarked(finalStateEventSetMap, nonFinalStateEventSetMap, statePair, targetStateA, targetStateB)) {
                    mark(statePair, dependentPairs);
                } else {
                    addAsDependent(statePair, dependentPairs, targetStateA, targetStateB);
                }
            }
        });
    }

    private void addAsDependent(Pair<Integer, Integer> statePair, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs, State targetStateA, State targetStateB) {
        dependentPairs.compute(Pair.of(targetStateA.getId(), targetStateB.getId()), (owner, dependentPairList) -> {
            if(dependentPairList != null){
                dependentPairList.add(statePair);
                return dependentPairList;
            } else {
                List<Pair<Integer, Integer>> dependents = new ArrayList<>();
                dependents.add(statePair);
                return dependents;
            }
        });
    }

    private boolean isNeedToBeMarked(Map<String, StateGroup> finalStateEventSetMap, Map<String, StateGroup> nonFinalStateEventSetMap, Pair<Integer, Integer> statePair, State targetStateA, State targetStateB) {
        if(marked.contains(Pair.of(targetStateA.getId(), targetStateB.getId())) || marked.contains(Pair.of(targetStateB.getId(), targetStateA.getId()))){
            return true;
        } else {
               StateGroup targetStateGroupA = findStateGroup(targetStateA, finalStateEventSetMap, nonFinalStateEventSetMap);
               StateGroup targetStateGroupB = findStateGroup(targetStateB, finalStateEventSetMap, nonFinalStateEventSetMap);
               return targetStateGroupA.id != targetStateGroupB.id;
        }
    }

    private void mark(Pair<Integer, Integer> statePair, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs){
        marked.add(statePair);
        if(dependentPairs.containsKey(statePair)){
            marked.addAll(getDependentsPairsRecursive(statePair, dependentPairs));
        }
    }

    private StateGroup findStateGroup(State state, Map<String, StateGroup> finalStateEventSetMap, Map<String, StateGroup> nonFinalStateEventSetMap) {
        return finalStateEventSetMap.values().stream()
                .filter(stateGroup -> stateGroup.states.contains(state))
                .findAny()
                .orElseGet(() -> nonFinalStateEventSetMap.values().stream()
                        .filter(stateGroup -> stateGroup.states.contains(state))
                        .findAny().get());
        }

    private List<Pair<Integer, Integer>> getDependentsPairsRecursive(Pair<Integer, Integer> owner, Map<Pair<Integer, Integer>, List<Pair<Integer, Integer>>> dependentPairs){
        List<Pair<Integer, Integer>> pairs = new ArrayList<>();
        dependentPairs.get(owner).forEach(dependentPair -> {
            pairs.add(dependentPair);
            pairs.addAll(getDependentsPairsRecursive(dependentPair, dependentPairs));
        });
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

    public DFA reduceByUnreachableStates(DFA initialDfa){
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
                StateGroup stateGroup = new StateGroup(currentStateGroupIndex++, state);
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
            for(int columnIndex = rowIndex+1; columnIndex < dfa.getStates().size()-1; columnIndex++ ){
                pairs.add(Pair.of(rowState.getId(), dfa.getStates().get(columnIndex).getId()));
            }
        }
        return pairs;
    }


}
