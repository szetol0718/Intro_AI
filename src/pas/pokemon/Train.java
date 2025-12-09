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

// FRAMEWORK IMPORTS
import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Agent;
import edu.bu.pas.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Team;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.generators.BattleCreator;

import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.nn.LossFunction;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.Optimizer;
import edu.bu.pas.pokemon.nn.losses.MeanSquaredError;
import edu.bu.pas.pokemon.nn.optimizers.*;
import edu.bu.pas.pokemon.training.data.Dataset;
import edu.bu.pas.pokemon.training.data.ReplacementType;
import edu.bu.pas.pokemon.training.data.ReplayBuffer;
import edu.bu.pas.pokemon.utils.Pair;
import edu.bu.pas.pokemon.utils.Triple;

public class Train {

    /* ===========================
       Utility helpers
       =========================== */

    private static Agent instantiate(String className) {
        try {
            Class<?> c = Class.forName(className);
            return (Agent) c.getConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("[Train] Could not instantiate agent: " + className);
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private static RewardFunction loadRewardFunction() {
        try {
            Class<?> c = Class.forName("src.pas.pokemon.rewards.CustomRewardFunction");
            return (RewardFunction) c.getConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("[Train] Failed to load CustomRewardFunction");
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private static Pair<Optimizer, LossFunction> buildTrainer(Namespace ns, Model model) {
        double lr   = ns.get("lr");
        double clip = ns.get("clip");

        Optimizer opt;
        switch (ns.get("optimizerType")) {
            case "sgd":
                opt = new SGDOptimizer(model.getParameters(), lr, -clip, clip);
                break;
            case "adam":
                opt = new AdamOptimizer(
                        model.getParameters(), lr,
                        ns.get("beta1"), ns.get("beta2"),
                        -clip, clip
                );
                break;
            default:
                throw new RuntimeException("Unknown optimizer type");
        }
        return new Pair<>(opt, new MeanSquaredError());
    }

    /* ===========================
       Training episode generation
       =========================== */

    private static void runTrainingEpisodes(
            NeuralQAgent agent,
            List<Agent> enemies,
            ReplayBuffer buffer,
            Namespace ns,
            Random rng
    ) {
        long totalGames = ns.get("numTrainingGames");

        for (int g = 0; g < totalGames; g++) {
            for (Agent opp : enemies) {
                try {
                    Battle battle = BattleCreator.makeRandomTeams(
                            6, 6, 4, rng, agent, opp
                    );

                    BattleView prevView = null;
                    MoveView prevAction = null;

                    while (!battle.isOver()) {
                        battle.nextTurn();
                        battle.applyPreTurnConditions();

                        BattleView curView = battle.getView();
                        Pair<Move, Move> moves = battle.getMoves();
                        MoveView a = moves.getFirst().getView();

                        battle.applyMoves(moves);
                        battle.applyPostTurnConditions();

                        BattleView nextView = battle.getView();

                        if (prevView != null) {
                            buffer.addSample(prevView, prevAction, nextView);
                        }

                        prevView   = nextView;
                        prevAction = a;
                    }

                    // wrap final transition
                    buffer.addSample(prevView, prevAction, battle.getView());

                    agent.afterGameEnds(battle.getView());
                    opp.afterGameEnds(battle.getView());

                } catch (Exception ex) {
                    System.err.println("[Train] Training episode error");
                    ex.printStackTrace();
                }
            }
        }
    }

    /* ===========================
       Model update loop (Q-Learning)
       =========================== */

    private static void updateQFunction(
            NeuralQAgent agent,
            Optimizer opt,
            LossFunction loss,
            ReplayBuffer buffer,
            RewardFunction reward,
            Namespace ns,
            Random rng
    ) {
        int epochs    = ns.get("numUpdates");
        int batchSize = ns.get("miniBatchSize");
        double gamma  = ns.get("gamma");

        Dataset data = buffer.toDataset(agent, gamma, reward);

        for (int ep = 0; ep < epochs; ep++) {
            data.shuffle();
            Dataset.BatchIterator it = data.iterator(batchSize);

            while (it.hasNext()) {
                Pair<Matrix, Matrix> batch = it.next();

                try {
                    Matrix preds = agent.getModel().forward(batch.getFirst());
                    Matrix grads = loss.backwards(preds, batch.getSecond());

                    opt.reset();
                    agent.getModel().backwards(batch.getFirst(), grads);
                    opt.step();
                } catch (Exception e) {
                    System.err.println("[Train] Backprop failed");
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
    }

    /* ===========================
       Evaluation Phase
       =========================== */

    private static Pair<Double, Double> evaluate(
            NeuralQAgent agent,
            List<Agent> enemies,
            RewardFunction rewardFn,
            Namespace ns,
            Random rng
    ) {
        long rounds = ns.get("numEvalGames");
        double gamma = ns.get("gamma");

        double utilSum = 0.0;
        double wins = 0.0;

        for (int r = 0; r < rounds; r++) {
            for (Agent opp : enemies) {

                double utility = 0.0;
                int t = 0;

                Battle battle = BattleCreator.makeRandomTeams(6, 6, 4, rng, agent, opp);

                BattleView prevView = null;
                MoveView prevAction = null;

                while (!battle.isOver()) {
                    battle.nextTurn();
                    battle.applyPreTurnConditions();

                    BattleView cur = battle.getView();
                    Pair<Move, Move> moves = battle.getMoves();
                    MoveView action = moves.getFirst().getView();

                    battle.applyMoves(moves);
                    battle.applyPostTurnConditions();

                    BattleView next = battle.getView();

                    if (prevView != null) {
                        double rwd = rewardFn.getStateActionStateReward(prevView, prevAction, next);
                        utility += Math.pow(gamma, t) * rwd;
                        t++;
                    }

                    prevView = next;
                    prevAction = action;
                }

                // final reward
                double finalReward =
                        rewardFn.getStateActionStateReward(prevView, prevAction, battle.getView());
                utility += Math.pow(gamma, t) * finalReward;

                utilSum += utility;

                // determine winner
                boolean oursAlive = battle.getTeam1().getPokemon().stream()
                        .anyMatch(p -> !p.hasFainted());
                boolean theirsAlive = battle.getTeam2().getPokemon().stream()
                        .anyMatch(p -> !p.hasFainted());

                if (oursAlive && !theirsAlive) wins++;
            }
        }

        return new Pair<>(utilSum / rounds, wins / rounds);
    }

    /* ===========================
       Main training driver
       =========================== */

    public static void main(String[] args)
    {
        // silence engine spam (but keep error logs)
        PrintStream realOut = System.out;
        System.setOut(new PrintStream(realOut) {
            @Override public void println(String msg) {}
            @Override public void print(String msg) {}
        });

        ArgumentParser parser = ArgumentParsers.newFor("Train").build()
                .defaultHelp(true)
                .description("Train a Neural Q-Agent in Pokémon RL.");

        parser.addArgument("enemyAgents").nargs("*");

        parser.addArgument("-p","--numCycles").type(Long.class).setDefault(1L);
        parser.addArgument("-t","--numTrainingGames").type(Long.class).setDefault(10L);
        parser.addArgument("-v","--numEvalGames").type(Long.class).setDefault(5L);

        parser.addArgument("-b","--maxBufferSize").type(Integer.class).setDefault(1280);
        parser.addArgument("-r","--replacementType").type(ReplacementType.class)
                .setDefault(ReplacementType.RANDOM);

        parser.addArgument("-u","--numUpdates").type(Integer.class).setDefault(1);
        parser.addArgument("-m","--miniBatchSize").type(Integer.class).setDefault(128);
        parser.addArgument("-n","--lr").type(Double.class).setDefault(1e-6);
        parser.addArgument("-c","--clip").type(Double.class).setDefault(100d);
        parser.addArgument("-d","--optimizerType").type(String.class).setDefault("sgd");
        parser.addArgument("-b1","--beta1").type(Double.class).setDefault(0.9);
        parser.addArgument("-b2","--beta2").type(Double.class).setDefault(0.999);

        parser.addArgument("-g","--gamma").type(Double.class).setDefault(1e-4);

        parser.addArgument("-i","--inFile").type(String.class).setDefault("");
        parser.addArgument("-o","--outFile").type(String.class).setDefault("./params/qFunction");
        parser.addArgument("--outOffset").type(Long.class).setDefault(1L);

        parser.addArgument("--seed").type(Long.class).setDefault(-1L);

        Namespace ns = parser.parseArgsOrFail(args);

        Random rng = new Random(ns.get("seed"));

        NeuralQAgent agent =
                (NeuralQAgent) instantiate("src.pas.pokemon.agents.PolicyAgent");
        agent.initialize(ns);

        RewardFunction rewardFn = loadRewardFunction();

        // load enemies
        List<Agent> enemies = new LinkedList<>();
        for (String cp : ns.get("enemyAgents")) {
            Agent e = instantiate(cp);
            e.initialize(ns);
            enemies.add(e);
        }

        Pair<Optimizer, LossFunction> trainCore =
                buildTrainer(ns, agent.getModel());

        ReplayBuffer buffer = new ReplayBuffer(
                ns.get("replacementType"),
                ns.get("maxBufferSize"),
                rng
        );

        long cycles = ns.get("numCycles");
        String outFileBase = ns.get("outFile");
        long offset = ns.get("outOffset");

        for (int c = 0; c < cycles; c++) {

            agent.train();
            runTrainingEpisodes(agent, enemies, buffer, ns, rng);

            updateQFunction(agent, trainCore.getFirst(), trainCore.getSecond(),
                    buffer, rewardFn, ns, rng);

            agent.getModel().save(outFileBase + (c + offset) + ".model");

            agent.eval();
            Pair<Double, Double> stats = evaluate(agent, enemies, rewardFn, ns, rng);

            realOut.println("cycle=" + c +
                    " avgReturn=" + stats.getFirst() +
                    " winRate=" + stats.getSecond());
        }
    }
}
