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
        // --- FIXED INPUT CALCULATION ---
        // We calculate the size manually to prevent the "120 != 1" error.
        int numTypes = Type.values().length; // Usually 18
        
        // Pokemon: 1(HP)+1(Lv)+7(Stats)+3(Hgt)+7(Status)+5(Flags)+numTypes(OneHot)+3(Misc) = 45
        int perPokemon = 27 + numTypes; 
        // Move: 5(Stats)+3(Cat)+numTypes(OneHot)+3(Hgt)+1(Eff) = 30
        int perMove = 12 + numTypes;
        
        // Matches the 120 features your sensor is producing
        int inputSize = (perPokemon * 2) + perMove; 

        // --- PROFESSOR'S SUGGESTION: SHALLOW NETWORK ---
        // Faster forward passes = Faster training cycles.
        Sequential qFunction = new Sequential();
        qFunction.add(new Dense(inputSize, 64)); // Single Hidden Layer
        qFunction.add(new ReLU());
        qFunction.add(new Dense(64, 1));         // Output
        
        System.err.println("[PolicyAgent] Shallow Model Initialized. Inputs: " + inputSize);
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
        // Fast Decay: Drops to < 5% random by Cycle 20 (1000 games)
        explorationRate = Math.max(0.05, 1.0 * Math.exp(-gamesPlayed / 500.0));
    }

    public void eval() {
        this.trainingMode = false;
        this.explorationRate = 0.0; 
    }

    @Override
    public MoveView getMove(BattleView view) {
        if (trainingMode && Math.random() < explorationRate) {
            // Guided Exploration: Pick Max Damage 60% of the time
            if (Math.random() < 0.6) {
                MoveView bestMove = null;
                double bestDamage = -1.0;
                TeamView oppTeam = this.getOpponentTeamView(view);
                PokemonView oppActive = oppTeam.getActivePokemonView();
                
                if (oppActive != null) {
                    for (MoveView move : this.getPotentialMoves(view)) {
                         double power = (move.getPower() != null) ? move.getPower() : 0.0;
                         double eff = Type.getEffectivenessModifier(move.getType(), oppActive.getCurrentType1());
                         if (oppActive.getCurrentType2() != null) eff *= Type.getEffectivenessModifier(move.getType(), oppActive.getCurrentType2());
                         
                         PokemonView me = this.getMyTeamView(view).getActivePokemonView();
                         if (me != null && (move.getType() == me.getCurrentType1() || move.getType() == me.getCurrentType2())) power *= 1.5;
                         
                         double dmg = power * eff;
                         if (dmg > bestDamage) { bestDamage = dmg; bestMove = move; }
                    }
                }
                if (bestMove != null && bestDamage > 0) return bestMove;
            }
            List<MoveView> moves = this.getPotentialMoves(view);
            if (!moves.isEmpty()) return moves.get(new Random().nextInt(moves.size()));
        }
        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view) {
        this.gamesPlayed++;
        if (this.gamesPlayed % 100 == 0) {
            System.out.println("Games: " + gamesPlayed + " | Eps: " + String.format("%.3f", explorationRate));
        }
    }
}