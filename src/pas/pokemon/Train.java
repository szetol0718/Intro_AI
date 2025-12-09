package src.pas.pokemon;

// SYSTEM IMPORTS
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import src.pas.pokemon.agents.PolicyAgent;
// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView; // Import PokemonView
import edu.bu.pas.pokemon.core.Team.TeamView;       // Import TeamView
import edu.bu.pas.pokemon.generators.BattleCreator;
import edu.bu.pas.pokemon.training.data.Dataset;
import edu.bu.pas.pokemon.training.data.ReplacementType;
import edu.bu.pas.pokemon.training.data.ReplayBuffer;
import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.nn.LossFunction;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.Optimizer;
import edu.bu.pas.pokemon.nn.losses.MeanSquaredError;
import edu.bu.pas.pokemon.nn.optimizers.*;
import edu.bu.pas.pokemon.utils.Pair; 

public class Train extends Object {

    public static Agent getAgent(String agentClassName) {
        Agent agent = null;
        try {
            Class<?> clazz = Class.forName(agentClassName);
            Constructor<?> constructor = clazz.getConstructor();
            agent = (Agent) constructor.newInstance();
        } catch (Exception e) {
            System.err.println("[ERROR] Train.getAgent: error when instantiating " + agentClassName);
            e.printStackTrace();
            System.exit(-1);
        }
        return agent;
    }

    public static RewardFunction getRewardFunction() {
        RewardFunction rewardFunction = null;
        try {
            Class<?> clazz = Class.forName("src.pas.pokemon.rewards.CustomRewardFunction");
            Constructor<?> constructor = clazz.getConstructor();
            rewardFunction = (RewardFunction) constructor.newInstance();
        } catch (Exception e) {
            System.err.println("[ERROR] Train.getRewardFunction: error instantiating CustomRewardFunction");
            e.printStackTrace();
            System.exit(-1);
        }
        return rewardFunction;
    }

    public static Pair<Optimizer, LossFunction> getModelAdjustmentInfrastructure(Namespace args, Model model) {
        final double lr = args.get("lr");
        final double clipValue = args.get("clip");

        Optimizer optim = null;
        if (args.get("optimizerType").equals("sgd")) {
            optim = new SGDOptimizer(model.getParameters(), lr, -clipValue, +clipValue);
        } else if (args.get("optimizerType").equals("adam")) {
            optim = new AdamOptimizer(model.getParameters(), lr, args.get("beta1"), args.get("beta2"), -clipValue, +clipValue);
        } else {
            System.err.println("[ERROR] Unknown optimizer type " + args.get("optimizerType"));
            System.exit(-1);
        }
        LossFunction lossFunction = new MeanSquaredError();
        return new Pair<>(optim, lossFunction);
    }

    public static void playTrainingGames(NeuralQAgent agent, List<Agent> enemyAgents, ReplayBuffer buffer, Namespace args, Random rng) {
        final long numTrainingGames = args.get("numTrainingGames");

        try {
            for (int gameIdx = 0; gameIdx < numTrainingGames; ++gameIdx) {
                for (Agent enemyAgent : enemyAgents) {
                    Battle battle = BattleCreator.makeRandomTeams(6, 6, 4, rng, agent, enemyAgent);

                    try {
                        BattleView oldView = null;
                        MoveView oldAction = null;
                        boolean isGameOver = false;

                        while (!isGameOver) {
                            battle.nextTurn();
                            battle.applyPreTurnConditions();

                            BattleView newView = battle.getView();
                            Pair<Move, Move> moves = battle.getMoves();
                            MoveView action = moves.getFirst().getView();
                            battle.applyMoves(moves);
                            battle.applyPostTurnConditions();

                            newView = battle.getView();
                            isGameOver = battle.isOver();

                            if (oldView != null) {
                                buffer.addSample(oldView, oldAction, newView);
                            }

                            oldView = newView;
                            oldAction = action;
                        }

                        agent.afterGameEnds(battle.getView());
                        enemyAgent.afterGameEnds(battle.getView());
                        
                        buffer.addSample(oldView, oldAction, battle.getView());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void update(NeuralQAgent agent, Optimizer optim, LossFunction lossFunction, ReplayBuffer replayBuffer, RewardFunction rewardFunction, Namespace args, Random rng) {
        double discountFactor = args.get("gamma");
        int batchSize = args.get("miniBatchSize");
        int numUpdates = args.get("numUpdates");

        Dataset dataset = replayBuffer.toDataset(agent, discountFactor, rewardFunction);

        for (int epochIdx = 0; epochIdx < numUpdates; ++epochIdx) {
            dataset.shuffle();
            Dataset.BatchIterator it = dataset.iterator(batchSize);
            while (it.hasNext()) {
                Pair<Matrix, Matrix> batch = it.next();
                try {
                    Matrix YHat = agent.getModel().forward(batch.getFirst());
                    optim.reset();
                    agent.getModel().backwards(batch.getFirst(), lossFunction.backwards(YHat, batch.getSecond()));
                    optim.step();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    public static Pair<Double, Double> playEvalGames(NeuralQAgent agent, List<Agent> enemyAgents, RewardFunction rewardFunction, Namespace args, Random rng) {
        final long numEvalGames = args.get("numEvalGames");
        final double discountFactor = args.get("gamma");

        double trajectoryUtilitySum = 0;
        double numWins = 0;

        try {
            for (int gameIdx = 0; gameIdx < numEvalGames; ++gameIdx) {
                for (Agent enemyAgent : enemyAgents) {
                    double trajectoryUtility = 0;
                    Battle battle = BattleCreator.makeRandomTeams(6, 6, 4, rng, agent, enemyAgent);

                    try {
                        BattleView oldView = null;
                        MoveView oldAction = null;
                        boolean isGameOver = false;
                        int t = 0;
                        while (!isGameOver) {
                            battle.nextTurn();
                            battle.applyPreTurnConditions();
                            BattleView newView = battle.getView();
                            Pair<Move, Move> moves = battle.getMoves();
                            MoveView action = moves.getFirst().getView();
                            battle.applyMoves(moves);
                            battle.applyPostTurnConditions();
                            newView = battle.getView();
                            isGameOver = battle.isOver();

                            if (oldView != null) {
                                double reward = 0d;
                                switch (rewardFunction.getType()) {
                                    case STATE: reward = rewardFunction.getStateReward(oldView); break;
                                    case STATE_ACTION: reward = rewardFunction.getStateActionReward(oldView, oldAction); break;
                                    case STATE_ACTION_STATE: reward = rewardFunction.getStateActionStateReward(oldView, oldAction, newView); break;
                                }
                                trajectoryUtility += Math.pow(discountFactor, t) * reward;
                                t += 1;
                            }
                            oldView = newView;
                            oldAction = action;
                        }

                        agent.afterGameEnds(battle.getView());
                        enemyAgent.afterGameEnds(battle.getView());

                        double reward = 0d;
                        switch (rewardFunction.getType()) {
                            case STATE: reward = rewardFunction.getStateReward(oldView); break;
                            case STATE_ACTION: reward = rewardFunction.getStateActionReward(oldView, oldAction); break;
                            case STATE_ACTION_STATE: reward = rewardFunction.getStateActionStateReward(oldView, oldAction, battle.getView()); break;
                        }
                        trajectoryUtility += Math.pow(discountFactor, t) * reward;
                        trajectoryUtilitySum += trajectoryUtility;

                        // FIX: Use Views instead of raw Team objects to avoid import errors
                        if (didTeamWin(battle.getView().getTeam1View(), battle.getView().getTeam2View())) {
                            numWins += 1;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new Pair<>(trajectoryUtilitySum / numEvalGames, numWins / numEvalGames);
    }
    
    // FIX: Changed parameters to TeamView
    private static boolean didTeamWin(TeamView t1, TeamView t2) {
        boolean t1Alive = false;
        // TeamView uses .size() and .getPokemonView()
        for(int i = 0; i < t1.size(); i++) {
            PokemonView p = t1.getPokemonView(i);
            if (p != null && !p.hasFainted()) t1Alive = true;
        }
        
        boolean t2Alive = false;
        for(int i = 0; i < t2.size(); i++) {
            PokemonView p = t2.getPokemonView(i);
            if (p != null && !p.hasFainted()) t2Alive = true;
        }
        
        return t1Alive && !t2Alive;
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        System.setOut(new PrintStream(out) {
            public void println(String message) {}
            public void print(String message) {}
        });

        ArgumentParser parser = ArgumentParsers.newFor("Train").build()
            .defaultHelp(true).description("Train a NeuralQAgent");

        parser.addArgument("enemyAgents").nargs("*").help("List of enemy agent classpaths");

        // TUNED DEFAULTS
        parser.addArgument("-p", "--numCycles").type(Long.class).setDefault(100l); 
        parser.addArgument("-t", "--numTrainingGames").type(Long.class).setDefault(500l); 
        parser.addArgument("-v", "--numEvalGames").type(Long.class).setDefault(20l);
        parser.addArgument("-b", "--maxBufferSize").type(Integer.class).setDefault(50000); 
        parser.addArgument("-r", "--replacementType").type(ReplacementType.class).setDefault(ReplacementType.RANDOM);
        parser.addArgument("-u", "--numUpdates").type(Integer.class).setDefault(10); 
        parser.addArgument("-m", "--miniBatchSize").type(Integer.class).setDefault(32); 
        parser.addArgument("-n", "--lr").type(Double.class).setDefault(1e-4); 
        parser.addArgument("-c", "--clip").type(Double.class).setDefault(100d);
        parser.addArgument("-d", "--optimizerType").type(String.class).setDefault("adam");
        parser.addArgument("-b1", "--beta1").type(Double.class).setDefault(0.9);
        parser.addArgument("-b2", "--beta2").type(Double.class).setDefault(0.999);
        parser.addArgument("-g", "--gamma").type(Double.class).setDefault(0.95); 

        parser.addArgument("-i", "--inFile").type(String.class).setDefault("");
        parser.addArgument("-o", "--outFile").type(String.class).setDefault("./params/qFunction");
        parser.addArgument("--outOffset").type(Long.class).setDefault(1l);
        parser.addArgument("--seed").type(Long.class).setDefault(-1l);

        Namespace ns = parser.parseArgsOrFail(args);
        final long numCycles = ns.get("numCycles");
        final long seed = ns.get("seed");
        String checkpointFileBase = ns.get("outFile");
        long offset = ns.get("outOffset");
        Random rng = new Random(seed);

        NeuralQAgent agent = (NeuralQAgent) getAgent("src.pas.pokemon.agents.PolicyAgent");
        agent.initialize(ns);
        RewardFunction rewardFunction = getRewardFunction();

        List<Agent> enemyAgents = new LinkedList<>();
        List<String> enemyAgentClassPaths = ns.get("enemyAgents");
        if (enemyAgentClassPaths.size() == 0) {
            System.err.println("[ERROR] Train.main: need to specify at least one enemy agent!");
            System.exit(1);
        } else {
            for (String classPath : enemyAgentClassPaths) {
                Agent enemyAgent = getAgent(classPath);
                enemyAgent.initialize(ns);
                enemyAgents.add(enemyAgent);
            }
        }

        Pair<Optimizer, LossFunction> adjustmentPair = getModelAdjustmentInfrastructure(ns, agent.getModel());
        Optimizer optim = adjustmentPair.getFirst();
        LossFunction lossFunction = adjustmentPair.getSecond();
        ReplayBuffer replayBuffer = new ReplayBuffer(ns.get("replacementType"), ns.get("maxBufferSize"), rng);

        for (int cycleIdx = 0; cycleIdx < numCycles; ++cycleIdx) {
            // FIX: Explicit CAST to PolicyAgent to access .train()
            if (agent instanceof PolicyAgent) {
                ((PolicyAgent) agent).train();
            }

            playTrainingGames(agent, enemyAgents, replayBuffer, ns, rng);
            update(agent, optim, lossFunction, replayBuffer, rewardFunction, ns, rng);
            agent.getModel().save(checkpointFileBase + (cycleIdx + offset) + ".model");

            // FIX: Explicit CAST to PolicyAgent to access .eval()
            if (agent instanceof PolicyAgent) {
                ((PolicyAgent) agent).eval();
            }
            
            Pair<Double, Double> statsPair = playEvalGames(agent, enemyAgents, rewardFunction, ns, rng);
            out.println("Cycle " + cycleIdx + " | Avg Utility: " + String.format("%.2f", statsPair.getFirst()) + " | Win Rate: " + String.format("%.2f", statsPair.getSecond()));
        }
    }
}