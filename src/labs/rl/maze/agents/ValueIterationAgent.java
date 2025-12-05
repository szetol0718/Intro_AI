package src.labs.rl.maze.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS
import edu.bu.labs.rl.maze.agents.StochasticAgent;
import edu.bu.labs.rl.maze.agents.StochasticAgent.RewardFunction;
import edu.bu.labs.rl.maze.agents.StochasticAgent.TransitionModel;
import edu.bu.labs.rl.maze.utilities.Coordinate;
import edu.bu.labs.rl.maze.utilities.Pair;



public class ValueIterationAgent
    extends StochasticAgent
{
    public static final double GAMMA = 0.9;
    public static final double EPSILON = 1e-6;

    private Map<Coordinate, Double> utilities;

    public ValueIterationAgent(int playerNum)
    {
        super(playerNum);
        this.utilities = null;
    }

    public Map<Coordinate, Double> getUtilities() { return this.utilities; }
    private void setUtilities(Map<Coordinate, Double> u) { this.utilities = u; }

    public boolean isTerminalState(Coordinate c)
    {
        return c.equals(StochasticAgent.POSITIVE_TERMINAL_STATE)
            || c.equals(StochasticAgent.NEGATIVE_TERMINAL_STATE);
    }

    public Map<Coordinate, Double> getInitialUtilityMap(StateView state)
    {
        Map<Coordinate, Double> map = new HashMap<>();
        for(int x = 0; x < state.getXExtent(); ++x)
        {
            for(int y = 0; y < state.getYExtent(); ++y)
            {
                if(!state.isResourceAt(x, y))
                {
                    map.put(new Coordinate(x, y), 0.0);
                }
            }
        }
        return map;
    }

    public void valueIteration(StateView state)
    {
        // U is $U_k$, U_prime is $U_{k+1}$
        Map<Coordinate, Double> U = getInitialUtilityMap(state);
        Map<Coordinate, Double> U_prime = new HashMap<>(U);
        
        // Initialize terminal states with their reward (fixed utility)
        U.put(POSITIVE_TERMINAL_STATE, RewardFunction.getReward(POSITIVE_TERMINAL_STATE));
        U.put(NEGATIVE_TERMINAL_STATE, RewardFunction.getReward(NEGATIVE_TERMINAL_STATE));
        U_prime.putAll(U);
        
        // Calculate convergence threshold: $\epsilon(1-\gamma)/\gamma$
        final double threshold = EPSILON * (1.0 - GAMMA) / GAMMA;
        double delta = Double.POSITIVE_INFINITY;

       // Loop until $\delta \le threshold$ [cite: 38]
        while (delta > threshold) {
            delta = 0.0;
            
            // Swap U and U_prime
            Map<Coordinate, Double> temp = U;
            U = U_prime;
            U_prime = temp;

            for (Coordinate s : U.keySet()) {
                // Skip terminal states [cite: 35, 36]
                if (isTerminalState(s)) {
                    U_prime.put(s, U.get(s)); // Preserve fixed utility
                    continue;
                }
                
                double maxExpectedUtility = Double.NEGATIVE_INFINITY;
                
                // Find $\max_{a} \sum_{s'} P(s' | s, a) U_k(s')$
                for (Direction action : TransitionModel.CARDINAL_DIRECTIONS) {
                    double expectedUtility = 0.0;
                    
                    Set<Pair<Coordinate, Double>> transitions = 
                        TransitionModel.getTransitionProbs(state, s, action);
                    
                    for (Pair<Coordinate, Double> transition : transitions) {
                        Coordinate nextS = transition.getFirst();
                        double prob = transition.getSecond();
                        
                        // Use $U_k$ (U map)
                        expectedUtility += prob * U.get(nextS);
                    }
                    
                    maxExpectedUtility = Math.max(maxExpectedUtility, expectedUtility);
                }
                
                // Bellman Update: $U_{k+1}(s) = R(s) + \gamma \max_{a} ...$
                double newUtility = RewardFunction.getReward(s) + GAMMA * maxExpectedUtility;
                U_prime.put(s, newUtility);
                
                // Update $\delta$: $\max_{s} |U_{k+1}(s) - U_k(s)|$
                delta = Math.max(delta, Math.abs(newUtility - U.get(s)));
            }
        }
        
        setUtilities(U_prime);
    }

    @Override
    public void computePolicy(StateView state,
                              HistoryView history)
    {
        this.valueIteration(state);

        Map<Coordinate, Direction> policy = new HashMap<>();

        for(Coordinate s : this.getUtilities().keySet())
        {
            if (isTerminalState(s)) continue;

            double maxActionUtility = Double.NEGATIVE_INFINITY;
            Direction bestDirection = null;

            for(Direction d : TransitionModel.CARDINAL_DIRECTIONS)
            {
                double thisActionUtility = 0.0;
                for(Pair<Coordinate, Double> transition : TransitionModel.getTransitionProbs(state, s, d))
                {
                    Coordinate nextS = transition.getFirst();
                    double prob = transition.getSecond();
                    
                    thisActionUtility += prob * this.getUtilities().get(nextS);
                }

                if(thisActionUtility > maxActionUtility)
                {
                    maxActionUtility = thisActionUtility;
                    bestDirection = d;
                }
            }

            policy.put(s, bestDirection);
        }

        this.setPolicy(policy);
    }
}