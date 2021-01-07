package offensive_bot;

import battlecode.common.RobotType;

public class UnitBuild {
    RobotType type;
    int influence;
    Communication.Message message;
    public UnitBuild(RobotType type, int influence, Communication.Message message) {
        this.type = type;
        this.influence = influence;
        this.message = message;
    }
}
