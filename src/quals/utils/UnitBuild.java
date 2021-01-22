package quals.utils;

import battlecode.common.RobotType;
import quals.Communication;

public class UnitBuild {
    public RobotType type;
    public double significance;
    public int priority = 0;
    public Communication.Message message;

    public UnitBuild(RobotType type, Communication.Message message, double significance) {
        this.type = type;
        this.significance = significance;
        this.message = message;
    }
}
