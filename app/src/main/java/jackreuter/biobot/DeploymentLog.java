package jackreuter.biobot;

public class DeploymentLog {
    String deploymentUser;
    String deploymentGPS;
    String boxID;
    String resetTime;
    String lightTurnedGreen;
    String lightStatus;
    String deploymentNotes;

    public DeploymentLog(
            String _deploymentUser,
            String _deploymentGPS,
            String _boxID,
            String _resetTime,
            String _lightTurnedGreen,
            String _lightStatus,
            String _deploymentNotes
    ) {
        deploymentUser = _deploymentUser;
        deploymentGPS = _deploymentGPS;
        boxID = _boxID;
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
