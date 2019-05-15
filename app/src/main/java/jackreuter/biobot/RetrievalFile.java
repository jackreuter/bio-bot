package jackreuter.biobot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class RetrievalFile {

    String filename;
    Map<String, String> fields;
    String[] columnNames;
    ArrayList<String[]> rows;

    public RetrievalFile(String name) {
        filename = name;
        fields = new LinkedHashMap<>();
        rows = new ArrayList<>();
    }

    public void setColumnNames(String[] names) {
        columnNames = names;
    }

    public void addField(String key, String value) {
        fields.put(key, value);
    }

    public void addRow(String[] row) {
        rows.add(row);
    }

    public String toString() {
        return filename;
    }

}
