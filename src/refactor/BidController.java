package refactor;

import battlecode.common.GameActionException;

import static refactor.EnlightenmentCenter.influenceMinimum;
import static refactor.Robot.rc;

public class BidController {

    static State state = State.KeepUp;

    static int prevTeamVotes = 0;
    static int prevBid = 2;
    static int lostInARow = 0;
    static double proportionNeeded = 0.5;
    static int winsNeeded = 1500;
    static boolean lostLast = false;

    public void update() throws GameActionException {
        // information upkeep
        lostLast = rc.getRoundNum() != 0 && rc.getTeamVotes() == prevTeamVotes;
        if (prevBid != 0) {
            lostInARow = lostLast ? lostInARow + 1 : 0;
        }
        winsNeeded = 1500 - rc.getTeamVotes();
        proportionNeeded = winsNeeded / (2999.0 - rc.getRoundNum());

        // state transitioning
        transition();
    }

    public void bid() throws GameActionException {
        // make the next bid
        int bid = state.suggestBid();
        prevBid = bid;
        prevTeamVotes = rc.getTeamVotes();
        if (bid != 0) {
            rc.bid(bid);
        }
    }

    static int bidlessBreak = 0;
    void transition() {
        if (state == State.OnABreak) {
            if (--bidlessBreak > 0)
                return;
        }

        if (lostInARow >= 4 && rc.getRoundNum() < 1500) {
            // take a break
            bidlessBreak = 50;
            state = State.OnABreak;
            lostInARow = 0;
            return;
        }

        if (proportionNeeded > 1.0)
            state = State.GiveUp;
        else if (rc.getRoundNum() > 2500)
            state = State.Endgame;
        else if (rc.getInfluence() > 4 * influenceMinimum() || proportionNeeded > 0.7)
            state = State.ScaleUp;
        else
            state = State.KeepUp;
    }

    private enum State {
        OnABreak, GiveUp,
        KeepUp {
            int suggestBid() {
                int bid = Math.max(prevBid, 2);
                if (lostLast)
                    bid += (int) (Math.random() * 3) + 1;
                return Math.min(bid, maxBid());
            }
        },
        ScaleUp {
            @Override
            int suggestBid() {
                int bid = Math.max(prevBid, 2);
                if (lostLast)
                    bid = (Math.max(bid, 2) * 3) / 2;
                return Math.min(bid, maxBid());
            }
        },
        Endgame {
            @Override
            int suggestBid() {
                int predictedInf = rc.getInfluence() +
                        (int) (21898 - 2.0 * Math.pow(rc.getRoundNum(), 1.5) / 15);
                if (Math.random() < proportionNeeded)
                    return predictedInf / winsNeeded;
                else
                    return 0;
            }
        };

        int suggestBid() {
            return 0;
        }
    }

    /* Helper Functions */
    static int maxBid() {
        return Math.max(Math.min(rc.getInfluence() - 2*influenceMinimum(), rc.getInfluence() / 5), 0);
    }
}
