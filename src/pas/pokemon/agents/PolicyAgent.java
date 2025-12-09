package src.pas.pokemon.agents;

// SYSTEM IMPORTS
import net.sourceforge.argparse4j.inf.Namespace;
import edu.bu.pas.pokemon.agents.NeuralQAgent;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.nn.Model;
import edu.bu.pas.pokemon.nn.models.Sequential;
import edu.bu.pas.pokemon.nn.layers.Dense;
import edu.bu.pas.pokemon.nn.layers.ReLU;
import edu.bu.pas.pokemon.nn.layers.Tanh;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Type;

// JAVA PROJECT IMPORTS
import src.pas.pokemon.senses.CustomSensorArray;
import java.util.Random;
import java.util.List;

public class PolicyAgent extends NeuralQAgent {
    
    private boolean trainingMode = false;
    private double explorationRate = 1.0; 
    private int gamesPlayed = 0;

    public PolicyAgent() { super(); }

    public void initializeSenses(Namespace args) {
        SensorArray modelSenses = new CustomSensorArray(this);
        this.setSensorArray(modelSenses);
    }

    @Override
    public void initialize(Namespace args) {
        System.err.println("[PolicyAgent] Initializing Agent...");
        this.initializeSenses(args);
        super.initialize(args);
    }

    @Override
    public Model initModel() {
        // --- DYNAMIC CALCULATOR ---
        
        int numTypes = Type.values().length;
        
        // 1. Pokemon Features (Scalars + One-Hot Types)
        // 2 (HP/Lvl) + 7 (Stats) + 3 (Height) + 7 (NVStatus) + 5 (Volatile) + 3 (Misc) = 27 Scalars
        int pokemonFeats = 27 + numTypes;
        
        // 2. Move Features
        // 5 (Stats) + 3 (Cat) + 3 (Height) + 1 (Eff) = 12 Scalars
        int moveFeats = 12 + numTypes;
        
        // 3. Team/Battle Features
        int globalFeats = 9; // 4 Team * 2 + 1 Battle
        
        // Total Input Size
        int inputSize = (pokemonFeats * 2) + moveFeats + globalFeats;

        // DEEP NETWORK
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(inputSize, 256)); 
        qFunction.add(new ReLU());
        qFunction.add(new Dense(256, 128));       
        qFunction.add(new Tanh());
        qFunction.add(new Dense(128, 1));         
        
        System.err.println("[PolicyAgent] Model Initialized. Inputs: " + inputSize);
        return qFunction;
    }

    @Override
    public Integer chooseNextPokemon(BattleView view) {
        TeamView myTeam = this.getMyTeamView(view);
        TeamView oppTeam = this.getOpponentTeamView(view);
        PokemonView oppActive = oppTeam.getActivePokemonView();
        
        Integer bestIdx = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int idx = 0; idx < myTeam.size(); ++idx) {
            PokemonView pokemon = myTeam.getPokemonView(idx);
            if (!pokemon.hasFainted()) {
                if (pokemon == myTeam.getActivePokemonView()) continue;
                double score = evaluatePokemonForSwitch(pokemon, oppActive);
                if (bestIdx == null || score > bestScore) {
                    bestIdx = idx;
                    bestScore = score;
                }
            }
        }
        return bestIdx != null ? bestIdx : 0;
    }

    private double evaluatePokemonForSwitch(PokemonView myPokemon, PokemonView oppPokemon) {
        double score = 0.0;
        double healthRatio = (double) myPokemon.getCurrentStat(Stat.HP) / myPokemon.getInitialStat(Stat.HP);
        score += healthRatio * 100.0;
        
        if (myPokemon.getNonVolatileStatus() != NonVolatileStatus.NONE) score -= 50.0;

        if (oppPokemon != null) {
            double eff = Type.getEffectivenessModifier(oppPokemon.getCurrentType1(), myPokemon.getCurrentType1());
            if (eff > 1.0) score -= 20.0; 
            if (eff < 1.0) score += 20.0; 
        }
        return score;
    }
    
    public void train() {
        this.trainingMode = true;
        explorationRate = Math.max(0.05, 1.0 * Math.exp(-gamesPlayed / 2000.0));
    }

    public void eval() {
        this.trainingMode = false;
        this.explorationRate = 0.0; 
    }

    @Override
    public MoveView getMove(BattleView view) {
        // 1. LETHAL CHECK
        MoveView lethal = getLethalMove(view);
        if (lethal != null) return lethal;

        // 2. EXPLORATION
        if (trainingMode && Math.random() < explorationRate) {
            if (Math.random() < 0.6) {
                MoveView bestMove = getBestDamageMove(view);
                if (bestMove != null) return bestMove;
            }
            List<MoveView> moves = this.getPotentialMoves(view);
            if (!moves.isEmpty()) return moves.get(new Random().nextInt(moves.size()));
        }
        return this.argmax(view);
    }

    private MoveView getLethalMove(BattleView view) {
        TeamView oppTeam = this.getOpponentTeamView(view);
        PokemonView oppActive = oppTeam.getActivePokemonView();
        if (oppActive == null) return null;
        double hp = oppActive.getCurrentStat(Stat.HP);
        
        for (MoveView move : this.getPotentialMoves(view)) {
            if (move.getPower() == null || move.getPower() <= 0) continue;
            if (calculateProjectedDamage(move, view, oppActive) >= hp) return move;
        }
        return null;
    }

    private MoveView getBestDamageMove(BattleView view) {
        TeamView oppTeam = this.getOpponentTeamView(view);
        PokemonView oppActive = oppTeam.getActivePokemonView();
        if (oppActive == null) return null;
        
        MoveView best = null;
        double maxDmg = -1.0;
        for (MoveView move : this.getPotentialMoves(view)) {
            double dmg = calculateProjectedDamage(move, view, oppActive);
            if (dmg > maxDmg) { maxDmg = dmg; best = move; }
        }
        return best;
    }

    private double calculateProjectedDamage(MoveView move, BattleView view, PokemonView oppActive) {
        if (move.getPower() == null) return 0.0;
        double power = move.getPower();
        double eff = Type.getEffectivenessModifier(move.getType(), oppActive.getCurrentType1());
        if (oppActive.getCurrentType2() != null) eff *= Type.getEffectivenessModifier(move.getType(), oppActive.getCurrentType2());
        
        PokemonView me = this.getMyTeamView(view).getActivePokemonView();
        if (me != null && (move.getType() == me.getCurrentType1() || move.getType() == me.getCurrentType2())) power *= 1.5;
        
        return power * eff;
    }

    @Override
    public void afterGameEnds(BattleView view) {
        this.gamesPlayed++;
        if (this.gamesPlayed % 100 == 0) {
            System.out.println("Games: " + gamesPlayed + " | Eps: " + String.format("%.3f", explorationRate));
        }
    }
}