package src.pas.pokemon.rewards;

// SYSTEM IMPORTS
import java.util.Map;
import java.util.Set;

// JAVA PROJECT IMPORTS
import edu.bu.pas.pokemon.agents.rewards.RewardFunction;
import edu.bu.pas.pokemon.agents.rewards.RewardFunction.RewardType;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.core.enums.Stat;
import edu.bu.pas.pokemon.core.enums.NonVolatileStatus;
import edu.bu.pas.pokemon.core.enums.Flag;
import edu.bu.pas.pokemon.core.enums.Type;

public class CustomRewardFunction extends RewardFunction {

    // --- WEIGHTS (Tuned based on Competitive Strategy Research) ---
    // Primary Goal: WIN > KILL > DAMAGE
    private static final double WIN_REWARD = 1000.0;
    private static final double LOSS_PENALTY = -1000.0;
    
    private static final double KO_REWARD = 100.0;     
    private static final double KO_PENALTY = -80.0;    
    
    // Aggression Bias: Reward dealing damage more than penalizing taking it.
    // This prevents "Stalling" behavior common in simple Q-agents.
    private static final double DAMAGE_DEALT_MULT = 60.0; 
    private static final double DAMAGE_TAKEN_MULT = 40.0; 
    
    // Status Weights (Gen 1 Meta: Sleep/Freeze are essentially KOs)
    private static final double STATUS_FREEZE_SLEEP = 35.0; 
    private static final double STATUS_PARA_TOXIC = 20.0;
    private static final double STATUS_BURN_POISON = 15.0;   
    
    // "Cheat Code": Explicitly reward selecting Super Effective moves
    // This speeds up early training by 10x-20x before the agent learns damage mechanics.
    private static final double TYPE_ADVANTAGE_BONUS = 15.0; 

    public CustomRewardFunction() {
        super(RewardType.STATE_ACTION_STATE);
    }

    @Override
    public double getLowerBound() { return -1000.0; }

    @Override
    public double getUpperBound() { return 1000.0; }

    @Override
    public double getStateReward(final BattleView state) { return 0.0; }

    @Override
    public double getStateActionReward(final BattleView state, final MoveView action) { return 0.0; }

    @Override
    public double getStateActionStateReward(final BattleView state,
                                            final MoveView action,
                                            final BattleView nextState) {
        double reward = 0.0;

        // 1. GAME OUTCOME (The "Sparse" Reward)
        if (nextState.isOver()) {
            if (didWeWin(nextState)) {
                return WIN_REWARD;
            } else {
                return LOSS_PENALTY;
            }
        }

        // 2. KOs (The "Step" Reward)
        reward += calculateKOReward(state, nextState);

        // 3. DAMAGE EXCHANGE (The "Dense" Reward)
        reward += calculateHPReward(state, nextState);

        // 4. STATUS EFFECTS (The "Strategic" Reward)
        reward += calculateStatusReward(state, nextState);

        // 5. TYPE MATCHUP (The "Heuristic" Reward)
        reward += calculateTypeEffectiveBonus(state, action);

        return Math.max(getLowerBound(), Math.min(getUpperBound(), reward));
    }

    // --- HELPER METHODS ---

    private boolean didWeWin(BattleView nextState) {
        // If enemy team is wiped, we won.
        TeamView enemyTeam = nextState.getTeam2View();
        for (int i = 0; i < enemyTeam.size(); i++) {
            if (!enemyTeam.getPokemonView(i).hasFainted()) return false;
        }
        return true;
    }

    private double calculateKOReward(BattleView state, BattleView nextState) {
        double reward = 0.0;
        int enemyFaintedDiff = countFainted(nextState.getTeam2View()) - countFainted(state.getTeam2View());
        int myFaintedDiff = countFainted(nextState.getTeam1View()) - countFainted(state.getTeam1View());

        if (enemyFaintedDiff > 0) reward += KO_REWARD * enemyFaintedDiff;
        if (myFaintedDiff > 0) reward += KO_PENALTY * myFaintedDiff;

        return reward;
    }

    private double calculateHPReward(BattleView state, BattleView nextState) {
        double reward = 0.0;

        PokemonView enemyBefore = state.getTeam2View().getActivePokemonView();
        PokemonView enemyAfter = nextState.getTeam2View().getActivePokemonView();
        
        // Ensure we don't reward "damage" if the enemy just switched pokemon
        if (enemyBefore != null && enemyAfter != null && isSamePokemon(enemyBefore, enemyAfter)) {
            double hpPctBefore = (double) enemyBefore.getCurrentStat(Stat.HP) / enemyBefore.getInitialStat(Stat.HP);
            double hpPctAfter = (double) enemyAfter.getCurrentStat(Stat.HP) / enemyAfter.getInitialStat(Stat.HP);
            double damageDealtPct = hpPctBefore - hpPctAfter;
            
            if (damageDealtPct > 0) {
                reward += damageDealtPct * DAMAGE_DEALT_MULT;
            }
        }

        PokemonView meBefore = state.getTeam1View().getActivePokemonView();
        PokemonView meAfter = nextState.getTeam1View().getActivePokemonView();
        
        if (meBefore != null && meAfter != null && isSamePokemon(meBefore, meAfter)) {
            double hpPctBefore = (double) meBefore.getCurrentStat(Stat.HP) / meBefore.getInitialStat(Stat.HP);
            double hpPctAfter = (double) meAfter.getCurrentStat(Stat.HP) / meAfter.getInitialStat(Stat.HP);
            double damageTakenPct = hpPctBefore - hpPctAfter;
            
            if (damageTakenPct > 0) {
                reward -= damageTakenPct * DAMAGE_TAKEN_MULT;
            }
        }
        return reward;
    }

    private double calculateStatusReward(BattleView state, BattleView nextState) {
        double reward = 0.0;
        PokemonView enemyBefore = state.getTeam2View().getActivePokemonView();
        PokemonView enemyAfter = nextState.getTeam2View().getActivePokemonView();

        if (enemyBefore != null && enemyAfter != null) {
            NonVolatileStatus statusBefore = enemyBefore.getNonVolatileStatus();
            NonVolatileStatus statusAfter = enemyAfter.getNonVolatileStatus();

            // Reward inflicting NEW status conditions
            if (statusBefore == NonVolatileStatus.NONE && statusAfter != NonVolatileStatus.NONE) {
                switch (statusAfter) {
                    case SLEEP:
                    case FREEZE:
                        reward += STATUS_FREEZE_SLEEP; // Massive reward (turn skip)
                        break;
                    case TOXIC:
                    case PARALYSIS:
                        reward += STATUS_PARA_TOXIC; // High reward (speed drop / increasing dmg)
                        break;
                    case POISON:
                    case BURN:
                        reward += STATUS_BURN_POISON; // Moderate reward (fixed dmg)
                        break;
                    default: break;
                }
            }
        }
        return reward;
    }

    private double calculateTypeEffectiveBonus(BattleView state, MoveView action) {
        if (action == null || action.getPower() <= 0) return 0.0;

        PokemonView enemy = state.getTeam2View().getActivePokemonView();
        if (enemy == null) return 0.0;

        double effectiveness = Type.getEffectivenessModifier(action.getType(), enemy.getCurrentType1());
        if (enemy.getCurrentType2() != null) {
            effectiveness *= Type.getEffectivenessModifier(action.getType(), enemy.getCurrentType2());
        }

        if (effectiveness >= 2.0) {
            return TYPE_ADVANTAGE_BONUS; 
        }
        return 0.0;
    }

    private int countFainted(TeamView team) {
        int count = 0;
        for (int i = 0; i < team.size(); i++) {
            if (team.getPokemonView(i).hasFainted()) count++;
        }
        return count;
    }

    private boolean isSamePokemon(PokemonView p1, PokemonView p2) {
        // Checking name is the safest way to track identity across turns in this engine
        return p1.getName().equals(p2.getName());
    }
}