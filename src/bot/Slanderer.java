package bot;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import bot.Communication.*;
import static bot.Communication.encode;
import static bot.Communication.decode;

public class Slanderer extends Robot{
    @Override
    void onAwake() throws GameActionException {

    }

    @Override
    void onUpdate() throws GameActionException {
        if(rc.isReady()) {
            Direction toMove = Direction.allDirections()[(int) (Math.random() * 8)];
            while(!rc.canMove(toMove)) {
                toMove =  Direction.allDirections()[(int) (Math.random() * 8)];
            }
            rc.move(toMove);
        }
    }
}
