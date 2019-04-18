package jackreuter.biobot;

public class DeploymentLog {
    String deploymentUser;
    String deploymentGPS;
    String boxID;
    String resetTime;
    String lightTurnedGreen;
    String deploymentNotes;

    public DeploymentLog(
            String _deploymentUser,
            String _deploymentGPS,
            String _boxID,
            String _resetTime,
            String _lightTurnedGreen,
            String _deploymentNotes
    ) {
        deploymentUser = _deploymentUser;
        deploymentGPS = _deploymentGPS;
        boxID = _boxID;
        resetTime = _resetTime;
        lightTurnedGreen = _lightTurnedGreen;
        deploymentNotes = _deploymentNotes;
    }
}