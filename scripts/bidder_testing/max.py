from strategy import Strategy


class MaxBid(Strategy):
    def __init__(self):
        pass

    def bid(self, round, influence, votes):
        return influence


DEFAULT = []
PARAMS = []
