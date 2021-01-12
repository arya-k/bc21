package sprint;

import battlecode.common.RobotType;

public class UnitBuild {
    RobotType type;
    int influence;
    int priority = 0;
    Communication.Message message;
    public UnitBuild(RobotType type, int influence, Communication.Message message) {
        this.type = type;
        this.influence = influence;
        this.message = message;
    }
}
