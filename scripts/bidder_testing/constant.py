from strategy import Strategy


class ConstantBid(Strategy):
    def __init__(self, constant):
        self.constant = constant

    def bid(self, round, influence, votes):
        return self.constant


DEFAULT = [20]
PARAMS = [[20, 30, 40]]
