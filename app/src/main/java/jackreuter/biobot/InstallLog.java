package jackreuter.biobot;

public class InstallLog {
    String deploymentUser;
    String deploymentGPS;
    String boxID;
    Boolean newBatteryInstalled;
    Boolean newPanelInstalled;
    Boolean inletAssemblyPluggedIn;
    String resetTime;
    String lightTurnedGreen;
    String lightStatus;
    String deploymentNotes;

    public InstallLog(
            String _deploymentUser,
            String _deploymentGPS,
            String _boxID,
            Boolean _newBatteryInstalled,
            Boolean _newPanelInstalled,
            Boolean _inletAssemblyPluggedIn,
            String _resetTime,
            String _lightTurnedGreen,
            String _lightStatus,
            String _deploymentNotes
    ) {
        deploymentUser = _deploymentUser;
        deploymentGPS = _deploymentGPS;
        boxID = _boxID;
        newBatteryInstalled = _newBatteryInstalled;
        newPanelInstalled = _newPanelInstalled;
        inletAssemblyPluggedIn = _inletAssemblyPluggedIn;
        resetTime = _resetTime;
        lightTurnedGreen = _lightTurnedGreen;
        lightStatus = _lightStatus;
        deploymentNotes = _deploymentNotes;
    }

    public static String getFieldName(String key) {
        switch (key) {
            case "deploymentUser":
                return "User";

            case "deploymentGPS":
                return "GPS coordinates";

            case "boxID":
                return "Box ID";

            case "newBatteryInstalled":
                return "New battery installed";

            case "newPanelInstalled":
                return "New panel installed";

            case "inletAssemblyPluggedIn":
                return "Inlet assembly plugged in";

            case "resetTime":
                return "Reset time";

            case "lightTurnedGreen":
                return "Light turned green";

            case "lightStatus":
                return "Light status";

            case "deploymentNotes":
                return "Notes";
        }
        return "";
    }
}
