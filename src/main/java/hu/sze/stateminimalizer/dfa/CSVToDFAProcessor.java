package hu.sze.stateminimalizer.dfa;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import hu.sze.stateminimalizer.dfa.model.DFA;
import hu.sze.stateminimalizer.dfa.model.State;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.*;

public class CSVToDFAProcessor {

    public DFA readCSVToDfa(InputStream inputStream) throws IOException {
        CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream), ';', '"');
        return processCSVInputData(csvReader.readAll());
    }
    public InputStream writeCSV(List<String[]> data) {

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            CSVWriter csvWriter = new CSVWriter(writer, ';');
            csvWriter.writeAll(data);
            csvWriter.close();
            return new ByteArrayInputStream(outputStream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    private DFA processCSVInputData(List<String[]> allRows) {
        Map<String, State> stateNameMap = new LinkedHashMap<>(); //order matters

        String[] headerContent = allRows.get(0);
        //input symbols are between the 1st and tha last column
        List<String> inputSymbolList = Arrays.asList(headerContent).subList(1, headerContent.length-1);
        Set<String> inputSymbols = new LinkedHashSet<>(inputSymbolList); //filter out duplicates

        for (int i = 1; i <allRows.size(); i++) {
            stateNameMap.put(allRows.get(i)[0], new State(i-1));
        }
        System.out.println(stateNameMap);
        State[] states = new State[stateNameMap.size()];
        Set<Integer> finalStatesIds = new LinkedHashSet<>();
        processStateFromCSV(allRows, stateNameMap, inputSymbolList, states, finalStatesIds);
        System.out.println(stateNameMap);
        System.out.println(finalStatesIds);

        DFA dfa = new DFA();
        dfa.getStates().addAll(Arrays.asList(states));
        dfa.getInputSymbols().addAll(inputSymbols);
        dfa.getFinalStateIds().addAll(finalStatesIds);
        return dfa;
    }

    private void processStateFromCSV(List<String[]> allRows, Map<String, State> stateNameMap, List<String> inputSymbolList, State[] states, Set<Integer> finalStates) {
        for (int rowIndex = 1; rowIndex < allRows.size(); rowIndex++) { //skip header
            String[] row = allRows.get(rowIndex);
            State state = stateNameMap.get(row[0]);
            state.setName(row[0]);
            fillStateTransitions(stateNameMap, inputSymbolList, row, state);
            states[rowIndex-1] = state;
            if(!StringUtils.isEmpty(row[row.length-1])){ //last column has content
                finalStates.add(state.getId());
            }
        }
    }

    private void fillStateTransitions(Map<String, State> stateNameMap, List<String> inputSymbolList, String[] row, State state) {
        for(int inputSymbolIndex = 0; inputSymbolIndex < inputSymbolList.size(); inputSymbolIndex++){
            String targetStateId = row[inputSymbolIndex+1];
            State targetState = stateNameMap.get(targetStateId);
            if (targetState != null) { //has transition
                state.getTransitions().put(inputSymbolList.get(inputSymbolIndex), targetState);
            }
        }
    }
}
