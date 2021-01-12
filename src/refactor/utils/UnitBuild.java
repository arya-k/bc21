package refactor.utils;

import battlecode.common.RobotType;
import refactor.Communication;

public class UnitBuild {
    public RobotType type;
    public int influence;
    public int priority = 0;
    public Communication.Message message;

    public UnitBuild(RobotType type, int influence, Communication.Message message) {
        this.type = type;
        this.influence = influence;
        this.message = message;
    }
}
