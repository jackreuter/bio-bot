package jackreuter.biobot;

public class RetrievalLog {
    String retrievalUser;
    String retrievalGPS;
    String retrievalNotes;

    public RetrievalLog(
            String _retrievalUser,
            String _retrievalGPS,
            String _retrievalNotes
    ) {
        retrievalUser = _retrievalUser;
        retrievalGPS = _retrievalGPS;
        retrievalNotes = _retrievalNotes;
    }
}
