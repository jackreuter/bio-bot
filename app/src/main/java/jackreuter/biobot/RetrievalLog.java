package jackreuter.biobot;

public class RetrievalLog {
    String retrievalUser;
    String retrievalGPS;
    String greenLedOn;
    String samplePutOnIce;
    String retrievalNotes;
    String qrCode;

    public RetrievalLog(
            String _retrievalUser,
            String _retrievalGPS,
            String _greenLedOn,
            String _samplePutOnIce,
            String _retrievalNotes,
            String _qrCode

    ) {
        retrievalUser = _retrievalUser;
        retrievalGPS = _retrievalGPS;
        greenLedOn = _greenLedOn;
        samplePutOnIce = _samplePutOnIce;
        qrCode = _qrCode;
        retrievalNotes = _retrievalNotes;
    }
}
