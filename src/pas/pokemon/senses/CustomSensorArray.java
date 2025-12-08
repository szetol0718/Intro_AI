package src.pas.pokemon.senses;

// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.linalg.Matrix;
import src.pas.pokemon.agents.PolicyAgent;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;

public class CustomSensorArray extends SensorArray {

    private PolicyAgent agent;
    private int numFeatures; // <--- Re-added this field!

    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
        this.numFeatures = 0;
    }
    
    // Required so PolicyAgent knows how large the input layer should be
    public int getNumFeatures() {
        return this.numFeatures;
    }

    /**
     * Converts the game state + a potential move into a vector of numbers (0.0 to 1.0).
     * The Neural Network uses this vector to predict "Q" (How good is this move?).
     */
    @Override
    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        List<Double> features = new ArrayList<>();

        TeamView myTeam = agent.getMyTeamView(state);
        TeamView enemyTeam = agent.getOpponentTeamView(state);
        PokemonView me = myTeam.getActivePokemonView();
        PokemonView enemy = enemyTeam.getActivePokemonView();

        // --- 1. HEALTH (0.0 to 1.0) ---
        features.add(getHPRatio(me)); 
        features.add(getHPRatio(enemy));

        // --- 2. MATCHUP STATS (Ratios) ---
        features.add(getStatRatio(me, enemy, Stat.ATK, Stat.DEF));       // Physical Matchup
        features.add(getStatRatio(me, enemy, Stat.SPATK, Stat.SPDEF));   // Special Matchup
        features.add(getStatRatio(me, enemy, Stat.SPD, Stat.SPD));       // Speed Matchup

        // --- 3. STATUS CONDITIONS ---
        addStatusFeatures(features, me);
        addStatusFeatures(features, enemy);

        // --- 4. MOVE ATTRIBUTES ---
        if (action != null) {
            // Power: Normalize (Max power ~150)
            features.add(action.getPower() != null ? action.getPower() / 150.0 : 0.0);
            
            // Accuracy: 0 to 1
            features.add(action.getAccuracy() != null ? action.getAccuracy() / 100.0 : 1.0);

            // Priority: Normalize (+1 is good)
            features.add((double) action.getPriority());

            // Type Effectiveness: Normalize (4.0 -> 1.0)
            double effectiveness = 1.0;
            if (enemy != null) {
                effectiveness = calculateTypeEffectiveness(action.getType(), enemy.getCurrentType1(), enemy.getCurrentType2());
            }
            features.add(effectiveness / 4.0); 

            // STAB
            boolean isStab = (action.getType() == me.getCurrentType1() || action.getType() == me.getCurrentType2());
            features.add(isStab ? 1.0 : 0.0);
            
            // Category
            features.add(action.getCategory() == Move.Category.PHYSICAL ? 1.0 : 0.0);
            features.add(action.getCategory() == Move.Category.SPECIAL ? 1.0 : 0.0);
            features.add(action.getCategory() == Move.Category.STATUS ? 1.0 : 0.0);
        } else {
            // Padding for null actions (e.g. switching)
            for(int i=0; i<8; i++) features.add(0.0);
        }

        // Update the feature count
        this.numFeatures = features.size();

        // Convert List -> Matrix
        Matrix rowVector = Matrix.zeros(1, features.size());
        for (int i = 0; i < features.size(); i++) {
            rowVector.set(0, i, features.get(i));
        }
        return rowVector;
    }

    // --- HELPERS ---

    private double getHPRatio(PokemonView p) {
        if (p == null || p.getInitialStat(Stat.HP) == 0) return 0.0;
        return (double) p.getCurrentStat(Stat.HP) / p.getInitialStat(Stat.HP);
    }

    private double getStatRatio(PokemonView me, PokemonView enemy, Stat myStat, Stat enemyStat) {
        if (me == null || enemy == null) return 0.5;

        double val1 = me.getCurrentStat(myStat);
        double val2 = enemy.getCurrentStat(enemyStat);

        if (val2 == 0) return 1.0; 
        
        double ratio = val1 / val2;
        // Clamp between 0.0 and 2.0 to prevent exploding values
        return Math.min(2.0, Math.max(0.0, ratio)); 
    }

    private void addStatusFeatures(List<Double> features, PokemonView p) {
        if (p == null) {
            for(int i=0; i<3; i++) features.add(0.0);
            return;
        }
        NonVolatileStatus s = p.getNonVolatileStatus();
        
        // 1. Incapacitated (Sleep/Freeze)
        features.add((s == NonVolatileStatus.SLEEP || s == NonVolatileStatus.FREEZE) ? 1.0 : 0.0);
        
        // 2. Slowed/Miss (Paralysis)
        features.add(s == NonVolatileStatus.PARALYSIS ? 1.0 : 0.0);
        
        // 3. DoT (Burn/Poison/Toxic)
        features.add((s == NonVolatileStatus.BURN || s == NonVolatileStatus.POISON || s == NonVolatileStatus.TOXIC) ? 1.0 : 0.0);
    }

    private double calculateTypeEffectiveness(Type moveType, Type defenderType1, Type defenderType2) {
        double effectiveness = 1.0;
        effectiveness *= Type.getEffectivenessModifier(moveType, defenderType1);
        if (defenderType2 != null) {
            effectiveness *= Type.getEffectivenessModifier(moveType, defenderType2);
        }
        return effectiveness;
    }
}
