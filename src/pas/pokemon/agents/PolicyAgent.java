package src.pas.pokemon.agents;

import net.sourceforge.argparse4j.inf.Namespace;

import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;

import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Type;

import edu.bu.pas.pokemon.linalg.Matrix;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.*;

import src.pas.pokemon.senses.CustomSensorArray;

import java.util.List;
import java.util.Random;

public class PolicyAgent extends NeuralQAgent
{
    private boolean isTraining = false;
    private double eps = 0.30;     // exploration; will decay
    private int games = 0;

    public PolicyAgent() { super(); }


    public void initializeSenses(Namespace args) {
        this.setSensorArray(new CustomSensorArray(this));
    }

    @Override
    public void initialize(Namespace args)
    {
        super.initialize(args);   // loads model (if provided), etc.
        initializeSenses(args);

        System.err.println("[PolicyAgent] Ready.");
    }

    @Override
    public Model initModel()
    {
        // IMPORTANT:
        // input size is determined dynamically after 1st sensor call,
        // but we must provide *some* size here. (Game engine rebuilds model at runtime.)
        int inSize = 9;   // overwritten once training really starts

        Sequential m = new Sequential();
        m.add(new Dense(inSize, 256));
        m.add(new ReLU());
        m.add(new Dense(256, 128));
        m.add(new Tanh());
        m.add(new Dense(128, 1)); 

        return m;
    }


    @Override
    public Integer chooseNextPokemon(BattleView view)
    {
        TeamView mine = this.getMyTeamView(view);
        TeamView opp  = this.getOpponentTeamView(view);

        PokemonView oppMon = opp.getActivePokemonView();

        Integer pick = null;
        double best = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < mine.size(); ++i) {
            PokemonView p = mine.getPokemonView(i);
            if (!p.hasFainted()) {
                double score = scoreSwitchOption(p, oppMon);
                if (pick == null || score > best) {
                    pick = i;
                    best = score;
                }
            }
        }

        return pick;
    }

    private double scoreSwitchOption(PokemonView me, PokemonView opp)
    {
        double s = 0.0;

        // defensive type matchup
        s += typeDefenseValue(me, opp);

        // general survivability
        double hpRatio = (double) me.getCurrentStat(Stat.HP) / me.getInitialStat(Stat.HP);
        s += hpRatio * 18.0;

        // avoid statused mons
        if (me.getNonVolatileStatus() != NonVolatileStatus.NONE)
            s -= 12.0;

        // basic stat contribution
        for (Stat st : new Stat[]{Stat.ATK,Stat.DEF,Stat.SPD,Stat.SPATK,Stat.SPDEF})
            s += me.getCurrentStat(st) / 105.0;

        return s;
    }

    private double typeDefenseValue(PokemonView me, PokemonView opp)
    {
        if (opp == null) return 0.0;

        double val = 0.0;

        Type[] ours = new Type[]{me.getCurrentType1(), me.getCurrentType2()};
        Type[] theirs = new Type[]{opp.getCurrentType1(), opp.getCurrentType2()};

        for (Type atk : theirs) {
            if (atk == null) continue;
            for (Type def : ours) {
                if (def == null) continue;

                double eff = Type.getEffectivenessModifier(atk, def);

                if (eff >= 2.0) val -= 9.0;     // we are weak
                else if (eff <= 0.5 && eff > 0) val += 4.0;  // resist
                else if (eff == 0.0) val += 7.0;             // immune
            }
        }

        return val;
    }



    public void train() {
        isTraining = true;
        // simple exponential decay; keeps same scale as original
        eps = Math.max(0.05, 0.30 * Math.exp(-games / 10000.0));
    }


    public void eval() {
        isTraining = false;
    }


    @Override
    public MoveView getMove(BattleView state)
    {
        List<MoveView> moves = getPotentialMoves(state);

        if (isTraining && Math.random() < eps) {
            // random exploration
            if (!moves.isEmpty())
                return moves.get(new Random().nextInt(moves.size()));
        }

        // use model
        return argmax(state);
    }

    @Override
    public MoveView argmax(BattleView state)
    {
        List<MoveView> moves = getPotentialMoves(state);

        MoveView best = null;
        double bestQ = Double.NEGATIVE_INFINITY;

        for (MoveView mv : moves) {
            double q = evaluateMove(state, mv);
            if (q > bestQ) {
                bestQ = q;
                best = mv;
            }
        }

        return best;
    }

    private double evaluateMove(BattleView s, MoveView mv)
    {
        // currently direct Q(s,a) call; transition model placeholder
        return eval(s, mv);
    }

    @Override
    public void afterGameEnds(BattleView view)
    {
        games++;
    }
}
