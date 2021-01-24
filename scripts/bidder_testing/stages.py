from strategy import Strategy
import random


class Stages(Strategy):
    def __init__(self, panic_factor, panic_frac):
        self.prevTeamVotes = 0
        self.prevBid = 2
        self.lostInARow = 0
        self.wonInARow = 0
        self.proportionNeeded = 0.5
        self.winsNeeded = 751
        self.lostLast = False
        self.state = "KeepUp"
        self.bid_func = {
            "OnABreak": self.zeroBid,
            "GiveUp": self.zeroBid,
            "ScaleUp": self.scaleUpBid,
            "KeepUp": self.keepUpBid,
            "Endgame": self.endgameBid,
        }

        self.scale_up_factor = 2
        self.scale_down_factor = 2
        self.panic_factor = panic_factor
        self.panic_frac = panic_frac

    def bid(self, rnd, influence, votes):
        self.update(rnd, influence, votes)
        bid = min(influence, self.bid_func[self.state](rnd, influence, votes))
        self.prevBid = bid
        self.prevTeamVotes = votes
        return bid

    def update(self, rnd, influence, votes):
        self.lostLast = rnd != 0 and votes == self.prevTeamVotes

        if self.prevBid != 0:
            self.lostInARow = self.lostInARow + 1 if self.lostLast else 0
        self.wonInARow = 0 if self.lostLast else self.wonInARow + 1

        self.winsNeeded = 751 - votes
        self.proportionNeeded = self.winsNeeded / (1500.0 - rnd)

        # state transitioning
        self.transition(rnd, influence, votes)

    def transition(self, rnd, influence, votes):
        if self.state == "OnABreak":
            self.bidlessBreak -= 1
            if self.bidlessBreak > 0:
                return

        if self.lostInARow >= 5 and self.proportionNeeded < 0.6 and rnd < 1350:
            # take a break
            self.bidlessBreak = 25
            self.state = "OnABreak"
            self.lostInARow = 0
            return

        if self.proportionNeeded > 1.0 or self.proportionNeeded <= 0:
            self.state = "GiveUp"
        elif rnd > 1350:
            self.state = "Endgame"
        elif (
            influence > self.panic_factor * self.influenceMinimum(rnd)
            or self.proportionNeeded > self.panic_frac
        ):
            self.state = "ScaleUp"
        else:
            self.state = "KeepUp"

    def zeroBid(self, rnd, influence, votes):
        return 0

    def keepUpBid(self, rnd, influence, votes):
        bid = max(self.prevBid, 2)
        if self.lostLast:
            bid += int(random.random() * 2) + 1
        elif self.wonInARow >= 4 and self.proportionNeeded < 0.55:
            bid = min(int(bid / self.scale_down_factor), bid - 1)
        return min(bid, self.maxBid(rnd, influence, votes))

    def scaleUpBid(self, rnd, influence, votes):
        bid = max(self.prevBid, 2)
        if self.lostLast:
            bid = max(int(bid * self.scale_up_factor), bid + 1)
        elif self.wonInARow >= 4 and self.proportionNeeded < 0.55:
            bid = min(int(bid / self.scale_down_factor), bid - 1)
        return min(bid, self.maxBid(rnd, influence, votes))

    def endgameBid(self, rnd, influence, votes):
        predictedInf = influence + int(7745 - 2.0 * (rnd ** 1.5) / 15)
        if random.random() < self.proportionNeeded + 0.1:
            return min(
                self.maxBid(rnd, influence, votes), predictedInf // self.winsNeeded
            )
        else:
            return 0

    def maxBid(self, rnd, influence, votes):
        factor = 15 if rnd < 300 else 7
        if rnd < 1450:
            return max(
                min(influence - 2 * self.influenceMinimum(rnd), influence / factor), 0
            )
        return influence

    def influenceMinimum(self, rnd):
        return 20 + int(rnd * 0.1)


DEFAULT = [6, 0.8]
PARAMS = [[4, 6, 8], [0.7, 0.8, 0.9]]