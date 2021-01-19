from strategy import Strategy


class ConstantBid(Strategy):
    # initialization logic. see below for what gets
    # passed in as a parameter
    def __init__(self, constant):
        self.constant = constant

    # returns a bid amount
    def bid(self, round, influence, votes):
        return self.constant


# the default argument list to be passed into __init__
# Running this strategy as a default_player (with -d)
# will use only this set of parameters
DEFAULT = [20]

# the i'th element of this list is all the possible values
# for the i'th argument passed into __init__. Running this
# strategy as a param_player (with -p) will iterate through
# every combination of possible parameters.
PARAMS = [[20, 30, 40]]
