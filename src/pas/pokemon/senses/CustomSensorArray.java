package src.pas.pokemon.senses;

import java.util.ArrayList;
import java.util.List;
import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.linalg.Matrix;
import src.pas.pokemon.agents.PolicyAgent;
import edu.bu.pas.pokemon.core.enums.Type;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.Height;

public class CustomSensorArray extends SensorArray {
    private PolicyAgent agent;
    private int numFeatures;

    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
        this.numFeatures = 0;
    }
    public int getNumFeatures() { return this.numFeatures; }

    @Override
    public Matrix getSensorValues(final BattleView state, final MoveView action) {
        List<Double> features = new ArrayList<>();

        // 1. POKEMON STATES (45 * 2 = 90 features)
        addPokemonState(features, state.getTeam1View().getActivePokemonView());
        addPokemonState(features, state.getTeam2View().getActivePokemonView());
        
        // 2. MOVE ATTRIBUTES (30 features)
        addMoveFeatures(features, state, action);
        
        this.numFeatures = features.size(); // Should be 120
        
        Matrix rowVector = Matrix.zeros(1, features.size());
        for (int i = 0; i < features.size(); i++) rowVector.set(0, i, features.get(i));
        return rowVector;
    }

    private void addPokemonState(List<Double> features, PokemonView pokemon) {
        if (pokemon == null) {
            // Pad 45 zeros (27 scalar + 18 types)
            for(int i = 0; i < 45; i++) features.add(0.0);
            return;
        }
        features.add((double) pokemon.getCurrentStat(Stat.HP) / pokemon.getInitialStat(Stat.HP));
        features.add((double) pokemon.getLevel() / 100.0);
        features.add((double) pokemon.getStatMultiplier(Stat.ATK) / 6.0);    
        features.add((double) pokemon.getStatMultiplier(Stat.DEF) / 6.0);
        features.add((double) pokemon.getStatMultiplier(Stat.SPD) / 6.0);
        features.add((double) pokemon.getStatMultiplier(Stat.SPATK) / 6.0);
        features.add((double) pokemon.getStatMultiplier(Stat.SPDEF) / 6.0);
        features.add((double) pokemon.getStatMultiplier(Stat.ACC) / 6.0);
        features.add((double) pokemon.getStatMultiplier(Stat.EVASIVE) / 6.0);
        
        Height height = pokemon.getHeight();
        features.add(height == Height.IN_AIR ? 1.0 : 0.0);
        features.add(height == Height.UNDERGROUND ? 1.0 : 0.0);  
        features.add(height == Height.NONE ? 1.0 : 0.0);
        
        NonVolatileStatus status = pokemon.getNonVolatileStatus();
        features.add(status == NonVolatileStatus.SLEEP ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.POISON ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.BURN ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.PARALYSIS ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.FREEZE ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.TOXIC ? 1.0 : 0.0);
        features.add(status == NonVolatileStatus.NONE ? 1.0 : 0.0);
        
        features.add(pokemon.getFlag(Flag.CONFUSED) ? 1.0 : 0.0);
        features.add(pokemon.getFlag(Flag.TRAPPED) ? 1.0 : 0.0);
        features.add(pokemon.getFlag(Flag.FLINCHED) ? 1.0 : 0.0);
        features.add(pokemon.getFlag(Flag.FOCUS_ENERGY) ? 1.0 : 0.0);
        features.add(pokemon.getFlag(Flag.SEEDED) ? 1.0 : 0.0);
        
        Type type1 = pokemon.getCurrentType1();
        Type type2 = pokemon.getCurrentType2();
        for (Type t : Type.values()) features.add((type1 == t || type2 == t) ? 1.0 : 0.0);
        
        features.add(pokemon.getActiveMoveView() != null ? 1.0 : 0.0);
        features.add(pokemon.getSubstitute() != null ? 1.0 : 0.0);
        features.add(pokemon.getStatsUnchangeable() ? 1.0 : 0.0);
    }

    private void addMoveFeatures(List<Double> features, BattleView state, MoveView action) {
        if (action == null) {
            // Pad 30 zeros (12 scalar + 18 types)
            for(int i=0; i<30; i++) features.add(0.0);
            return;
        }
        features.add(action.getPower() != null ? (double) action.getPower() / 200.0 : 0.0);
        features.add(action.getAccuracy() != null ? (double) action.getAccuracy() / 100.0 : 1.0);
        features.add((double) action.getPP() / 40.0);
        features.add((double) action.getPriority());
        features.add((double) action.getCriticalHitRatio());
        
        features.add(action.getCategory() == Move.Category.PHYSICAL ? 1.0 : 0.0);
        features.add(action.getCategory() == Move.Category.SPECIAL ? 1.0 : 0.0);
        features.add(action.getCategory() == Move.Category.STATUS ? 1.0 : 0.0);
        
        Type moveType = action.getType();
        for (Type t : Type.values()) features.add(moveType == t ? 1.0 : 0.0);
        
        features.add(action.getCanHitHeights().contains(Height.IN_AIR) ? 1.0 : 0.0);
        features.add(action.getCanHitHeights().contains(Height.UNDERGROUND) ? 1.0 : 0.0);
        features.add(action.getCanHitHeights().contains(Height.NONE) ? 1.0 : 0.0);
        
        PokemonView opponent = state.getTeam2View().getActivePokemonView();
        if (opponent != null && action.getPower() != null && action.getPower() > 0) {
            double effectiveness = calculateTypeEffectiveness(action.getType(), opponent.getCurrentType1(), opponent.getCurrentType2());
            features.add(effectiveness / 4.0); 
        } else {
            features.add(0.25);
        }
    }
    
    private double calculateTypeEffectiveness(Type moveType, Type defenderType1, Type defenderType2) {
        double effectiveness = 1.0;
        effectiveness *= Type.getEffectivenessModifier(moveType, defenderType1);
        if (defenderType2 != null) effectiveness *= Type.getEffectivenessModifier(moveType, defenderType2);
        return effectiveness;
    }
}