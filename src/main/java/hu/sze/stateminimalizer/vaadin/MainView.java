package hu.sze.stateminimalizer.vaadin;


import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.InputStreamFactory;
import com.vaadin.flow.server.StreamResource;
import hu.sze.stateminimalizer.dfa.Minimizer;
import hu.sze.stateminimalizer.dfa.model.DFA;
import hu.sze.stateminimalizer.dfa.model.State;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Route("")
public class MainView extends HorizontalLayout {

    private static final String CELL_SIZE = "3em";

    private Button startButton;
    private Button continueButton;
    private VerticalLayout errorLayout =new VerticalLayout();
    private VerticalLayout treeLayout = new VerticalLayout();
    private VerticalLayout mainLayout = new VerticalLayout();


    private DFA dfa;


    public MainView() {
        add(mainLayout);
        mainLayout.add(new H2("Véges automata minimalizálás"));


        Anchor anchor = new Anchor(getExampleFile(), "Minta csv letöltése");
        anchor.getStyle().set("font-size", "1em");

        mainLayout.add(new H4("Automata megadása fájl feltöltéssel:"));
        Upload fileUploader = createFileUploader();


        mainLayout.add(fileUploader, anchor);

        startButton = new Button("Indítás");
        startButton.setEnabled(false);
        startButton.addClickListener(buttonClickEvent ->  startMinimizeDfa());
        continueButton = new Button("Aktív event set-ek");
        continueButton.setVisible(false);
        mainLayout.add(startButton, continueButton, errorLayout);
    }

    private Upload createFileUploader() {
        MemoryBuffer memoryBuffer = new MemoryBuffer();

        Upload fileUploader = new Upload();

        fileUploader.setReceiver(memoryBuffer);
        fileUploader.setAcceptedFileTypes(".csv");
        fileUploader.setMaxFileSize(10000);
        fileUploader.setAutoUpload(true);
        fileUploader.setMaxFiles(1);
        UploadI18N uploadI18N =new UploadI18N();
        uploadI18N.setCancel("Mégsem");
        uploadI18N.setUploading(new UploadI18N.Uploading().setStatus(new UploadI18N.Uploading.Status()));
        uploadI18N.setAddFiles(new UploadI18N.AddFiles().setMany("Fájlok kiválasztása").setOne("Fájl kiválasztása"));
        uploadI18N.setDropFiles(new UploadI18N.DropFiles().setMany("Fájlok idehúzása").setOne("Húzd ide a fájlt"));

        fileUploader.setI18n(uploadI18N);
        fileUploader.addSucceededListener(succeededEvent -> handleUploadedFile(memoryBuffer));
        return fileUploader;
    }

    private void handleUploadedFile(MemoryBuffer memoryBuffer) {
        errorLayout.removeAll();
        try {
            InputStream inputStream = memoryBuffer.getInputStream();
            CSVReader csvReader = new CSVReader(new InputStreamReader(inputStream), ';', '"');

            List<String[]> allRows = csvReader.readAll();
            this.dfa = processCsvData(allRows);;

            getUI().ifPresent(ui -> ui.accessSynchronously(() -> startButton.setEnabled(true)));
        } catch (Exception e) {
            Label errorLabel = createErrorLabel("Hiba történt a feldolgozás során!");
            errorLayout.add(errorLabel);
            e.printStackTrace();
        }
    }

    private DFA processCsvData(List<String[]> allRows) {
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
        processStateFromCsv(allRows, stateNameMap, inputSymbolList, states, finalStatesIds);
        System.out.println(stateNameMap);
        System.out.println(finalStatesIds);

        DFA dfa = new DFA();
        dfa.getStates().addAll(Arrays.asList(states));
        dfa.getInputSymbols().addAll(inputSymbols);
        dfa.getFinalStateIds().addAll(finalStatesIds);
        return dfa;
    }

    private void processStateFromCsv(List<String[]> allRows, Map<String, State> stateNameMap, List<String> inputSymbolList, State[] states, Set<Integer> finalStates) {
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

    private Label createErrorLabel(String content){
        Label errorLabel = new Label(content);
        errorLabel.getStyle().set("color", "red");
        return errorLabel;
    }

    private StreamResource getExampleFile(){
        errorLayout.removeAll();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(outputStream);
            CSVWriter csvWriter = new CSVWriter(writer, ';');
            csvWriter.writeAll(getExampleDfaRawData());
            csvWriter.close();

            StreamResource streamResource = new StreamResource("minta.csv", (InputStreamFactory) () -> new ByteArrayInputStream(outputStream.toByteArray()));
            streamResource.setContentType("text/csv");
            return streamResource;
        } catch (Exception e) {
            errorLayout.add(createErrorLabel("Hiba történt!"));
            e.printStackTrace();
        }
        return null;
    }

    private List<String[]> getExampleDfaRawData(){
        List<String[]> dfaArrays = new ArrayList<>();
        dfaArrays.add("States\\Input symbols;a;b;Final state?".split(";")); //header
        dfaArrays.add("q0;q3;q1;t".split(";"));
        dfaArrays.add("q1;;q2;".split(";"));
        dfaArrays.add("q2;q0;;".split(";"));
        dfaArrays.add("q3;q4;;".split(";"));
        dfaArrays.add("q4;;q0;".split(";"));

        return dfaArrays;
    }

    private void startMinimizeDfa(){
        add(treeLayout);

        startButton.setVisible(false);
        Minimizer minimizer = new Minimizer(dfa);

        DFA minDfa = minimizer.reduceByUnreachableStates();

        System.out.println(minDfa);

        minimizer.groupByIsFinalState();
        mainLayout.add(new Label("Σ: " + String.join(", ", minDfa.getInputSymbols())));
        mainLayout.add(new Label("K: "+ minDfa.getStates().stream().map(state -> state.getName()).collect(Collectors.joining(", "))));
        mainLayout.add(new Label("S: "+ minDfa.getInitialState().getName()));
        mainLayout.add(new Label("F: "+ minDfa.getFinalStates().stream().map(state -> state.getName()).collect(Collectors.joining(", "))));

        Component redTree = createTree(minDfa, minimizer, false);
        treeLayout.add(redTree);
        continueButton.setVisible(true);
        continueButton.addClickListener(buttonClickEvent -> {
            treeLayout.remove(redTree);
            continueButton.setVisible(false);
            minimizer.groupByEventSet();
            treeLayout.add(createTree(minDfa, minimizer, true));
        });
    }

    private Component createTree(DFA dfa, Minimizer minimizer, boolean showFullTree){
        VerticalLayout table = new VerticalLayout();
        table.setSpacing(false);
        for (int rowIndex = 0; rowIndex < dfa.getStates().size(); rowIndex++){
            State rowState = dfa.getStates().get(rowIndex);
            HorizontalLayout row = new HorizontalLayout();
            row.setHeight(CELL_SIZE);
            row.setAlignItems(Alignment.CENTER);
            row.setSpacing(false);
            for(int columnIndex = 0; columnIndex<rowIndex; columnIndex++){
                Component cell = createCellComponent(dfa, minimizer, rowIndex, rowState, columnIndex, showFullTree);
                row.add(cell);
            }
            Label headerLabel = createCellLabel();
            headerLabel.setText(rowState.getName());
            headerLabel.getStyle().set("border", "none");
            row.add(headerLabel);
            table.add(row);
        }
        return table;
    }

    private Component createCellComponent(DFA dfa, Minimizer minimizer, int rowIndex, State rowState, int columnIndex, boolean showFull) {
        State columnState = dfa.getStates().get(columnIndex);
        HasStyle cell;
        if(!minimizer.mergeableByIsFinal(rowState, columnState)){
            cell = createCellLabel();
            fillXLabel((Label) cell, "Piros: végállapot vs. nem végállapot", "red");
        } else if(showFull) {
            if (!minimizer.isSameActiveEventSet(rowState, columnState)) {
                cell = createCellLabel();
                fillXLabel((Label) cell, "Kék: eltérő event set: {" + minimizer.getActiveEventSet(rowState) + "} <-> {" + minimizer.getActiveEventSet(columnState) + "}", "blue");
            } else {
                cell = createButtonCell();
            }
        } else {
            cell = createCellLabel();
        }
        if(rowIndex != dfa.getStates().size() - 1){ //has more rows
            cell.getStyle().set("border-bottom", "none");
        }
        if(columnIndex != rowIndex - 1){ //has more columns
            cell.getStyle().set("border-right", "none");
        }
        return (Component) cell;
    }

    private HorizontalLayout createButtonCell() {
        Button button = new Button();
        button.setIcon(new Icon(VaadinIcon.POINTER));
        button.addClickListener(buttonClickEvent -> Notification.show("Még nincs kész"));

        HorizontalLayout horizontalLayout = new HorizontalLayout(button);
        horizontalLayout.setMargin(false);
        horizontalLayout.setAlignItems(Alignment.CENTER);
        horizontalLayout.setWidth(CELL_SIZE);
        horizontalLayout.setHeight(CELL_SIZE);
        horizontalLayout.getStyle().set("border", "1px solid black");
        horizontalLayout.getStyle().set("border-radius", "0 !important");
        return horizontalLayout;
    }

    private Label createCellLabel(){
        Label label = new Label("");
        label.getStyle().set("font-weight", "bold");
        label.getStyle().set("text-align", "center");
        label.getStyle().set("width", CELL_SIZE);
        label.getStyle().set("height", CELL_SIZE);
        label.getStyle().set("border", "1px solid black");
        return label;
    }

    private void fillXLabel(Label label, String title, String color){
        label.setText("X");
        label.getStyle().set("color", color);
        label.getElement().setAttribute("title", title);
    }
}
