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
    
    // TRAINING HYPERPARAMETERS
    private boolean trainingMode = false;
    // We want a high start (1.0) to explore everything early, then decay
    private double explorationRate = 1.0; 
    private int gamesPlayed = 0;

    public PolicyAgent() {
        super();
    }

    /**
     * Step 1: Initialize the "Eyes" (Sensor Array)
     */
    public void initializeSenses(Namespace args) {
        // This connects the agent to the CustomSensorArray we just wrote
        SensorArray modelSenses = new CustomSensorArray(this);
        this.setSensorArray(modelSenses);
    }

    /**
     * Step 2: Initialize the Agent and Model
     */
    @Override
    public void initialize(Namespace args) {
        // This calls initModel(), so we need to set things up before calling super
        System.err.println("[PolicyAgent] Initializing Agent...");
        
        // Initialize senses FIRST so initModel knows the input size
        this.initializeSenses(args);
        
        super.initialize(args);
    }

    /**
     * Step 3: Define the "Brain" (Neural Network Architecture)
     */
    @Override
    public Model initModel() {
        // 1. Get the input size dynamically from our Sensor Array logic
        // We calculated this in CustomSensorArray: 
        // 2 (HP) + 3 (Matchup) + 6 (Status) + 8 (Move) = 19 inputs
        int inputSize = 19; 
        
        // 2. Define the Network
        // Architecture: 19 Inputs -> 64 Hidden (ReLU) -> 1 Output (Q-Value)
        // Kept small/medium to prevent overfitting and speed up training
        Sequential qFunction = new Sequential();
        
        qFunction.add(new Dense(inputSize, 64)); 
        qFunction.add(new ReLU());
        // qFunction.add(new Dense(64, 32)); // Optional deeper layer if training > 10k games
        // qFunction.add(new ReLU());
        qFunction.add(new Dense(64, 1)); // Output: The predicted value of this move
        
        System.err.println("[PolicyAgent] Model Initialized with Input Size: " + inputSize);
        return qFunction;
    }

    /**
     * Step 4: Switching Logic (Heuristic)
     * When our pokemon faints, who do we send out next?
     */
    @Override
    public Integer chooseNextPokemon(BattleView view) {
        TeamView myTeam = this.getMyTeamView(view);
        TeamView oppTeam = this.getOpponentTeamView(view);
        PokemonView oppActive = oppTeam.getActivePokemonView();
        
        Integer bestIdx = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int idx = 0; idx < myTeam.size(); ++idx) {
            PokemonView pokemon = myTeam.getPokemonView(idx);
            
            // Can't switch to a fainted pokemon
            if (!pokemon.hasFainted()) {
                // If it's the currently active pokemon (and we are forced to switch), skip it
                if (pokemon == myTeam.getActivePokemonView()) continue;

                double score = evaluatePokemonForSwitch(pokemon, oppActive);
                
                if (bestIdx == null || score > bestScore) {
                    bestIdx = idx;
                    bestScore = score;
                }
            }
        }
        
        // If we found no one (shouldn't happen unless lost), return default
        return bestIdx != null ? bestIdx : 0;
    }

    // Heuristic: Score a bench pokemon against the current enemy
    private double evaluatePokemonForSwitch(PokemonView myPokemon, PokemonView oppPokemon) {
        double score = 0.0;
        
        // 1. HP Percentage (Prioritize healthy mons)
        double healthRatio = (double) myPokemon.getCurrentStat(Stat.HP) / myPokemon.getInitialStat(Stat.HP);
        score += healthRatio * 50.0;
        
        // 2. Speed Advantage (Moving first is huge)
        if (oppPokemon != null) {
            double mySpd = myPokemon.getCurrentStat(Stat.SPD);
            double oppSpd = oppPokemon.getCurrentStat(Stat.SPD);
            if (mySpd > oppSpd) score += 20.0;
        }

        // 3. Type Matchup (Do we resist them? Do we hit them hard?)
        if (oppPokemon != null) {
            score += calculateTypeMatchupScore(myPokemon, oppPokemon);
        }
        
        return score;
    }

    private double calculateTypeMatchupScore(PokemonView myPokemon, PokemonView oppPokemon) {
        double score = 0.0;
        Type oppType1 = oppPokemon.getCurrentType1();
        Type oppType2 = oppPokemon.getCurrentType2();
        Type myType1 = myPokemon.getCurrentType1();
        
        // Defensive: Do we resist their STAB types?
        if (myType1 != null) {
            if (oppType1 != null) {
                double eff = Type.getEffectivenessModifier(oppType1, myType1);
                if (eff < 1.0) score += 15.0; // Resistance
                if (eff > 1.0) score -= 20.0; // Weakness (Avoid!)
            }
            if (oppType2 != null) {
                double eff = Type.getEffectivenessModifier(oppType2, myType1);
                if (eff < 1.0) score += 15.0;
                if (eff > 1.0) score -= 20.0;
            }
        }
        return score;
    }
    
    // --- TRAINING LOGIC ---


    public void train() {
        this.trainingMode = true;
        // Decay exploration: Starts at 1.0, decays to ~0.05 over 3000 games
        // Adjust the divisor (3000.0) based on how many games you run in Train.java
        explorationRate = Math.max(0.05, 1.0 * Math.exp(-gamesPlayed / 3000.0));
    }


    public void eval() {
        this.trainingMode = false;
        this.explorationRate = 0.0; // No randomness in matches
    }

    @Override
    public MoveView getMove(BattleView view) {
        // Epsilon-Greedy Strategy
        if (trainingMode && Math.random() < explorationRate) {
            // Explore: Pick a random move
            List<MoveView> moves = this.getPotentialMoves(view);
            if (!moves.isEmpty()) {
                return moves.get(new Random().nextInt(moves.size()));
            }
        }
        
        // Exploit: Pick the best move according to our Brain (Q-Function)
        return this.argmax(view);
    }

    @Override
    public void afterGameEnds(BattleView view) {
        this.gamesPlayed++;
        // Print progress every 100 games
        if (this.gamesPlayed % 100 == 0) {
            System.out.println("Games Played: " + gamesPlayed + " | Epsilon: " + String.format("%.3f", explorationRate));
        }
    }
}