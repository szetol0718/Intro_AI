package src.pas.pokemon.rewards;

// SYSTEM IMPORTS
import java.util.List;

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
import edu.bu.pas.pokemon.core.enums.Height;

public class CustomRewardFunction extends RewardFunction {

    public CustomRewardFunction() {
        super(RewardType.STATE_ACTION_STATE);
    }

    @Override
    public double getLowerBound() { return -1.0; } // Scaled down
    @Override
    public double getUpperBound() { return 1.0; }  // Scaled down

    @Override
    public double getStateReward(final BattleView state) { return 0d; }
    @Override
    public double getStateActionReward(final BattleView state, final MoveView action) { return 0d; }

    @Override
    public double getStateActionStateReward(final BattleView state,
                                            final MoveView action,
                                            final BattleView nextState) {
        double reward = 0.0;

        // 1. GAME OUTCOME (The most important signal)
        // Scaled to 1.0 / -1.0
        if (nextState.isOver()) {
            return calculateGameOutcomeReward(state, nextState);
        }

        // 2. KO REWARD (Momentum swing)
        // Scaled: ~0.3
        reward += calculateKOReward(state, nextState);

        // 3. DAMAGE EXCHANGE (Efficiency)
        // Scaled: ~0.2
        reward += calculateDamageExchangeReward(state, nextState);

        // 4. STATUS EFFECTS (Control)
        // Scaled: ~0.1 - 0.2
        reward += calculateStatusEffectReward(state, nextState);

        // 5. STRATEGIC MOVE (Immediate type effectiveness)
        // Scaled: ~0.1
        reward += calculateStrategicMoveReward(state, action, nextState);
        
        // 6. RESOURCE / MOMENTUM
        // Scaled: ~0.1
        reward += calculateMomentumReward(state, nextState);

        // Clamp to [-1.0, 1.0] to prevent gradient explosion
        return Math.max(getLowerBound(), Math.min(getUpperBound(), reward));
    }

    // --- HELPER METHODS ---

    private double calculateGameOutcomeReward(final BattleView state, final BattleView nextState) {
        if (!nextState.isOver()) return 0.0;
        
        // Check if we won by looking at opponent's team status
        boolean weWon = true;
        TeamView theirTeam = nextState.getTeam2View();
        for (int i = 0; i < theirTeam.size(); i++) {
            if (!theirTeam.getPokemonView(i).hasFainted()) {
                weWon = false;
                break;
            }
        }
        
        if (weWon) return 1.0; // Win
        else return -1.0;      // Loss
    }

    private double calculateKOReward(final BattleView state, final BattleView nextState) {
        double reward = 0.0;
        
        int theirDeadBefore = countFainted(state.getTeam2View());
        int theirDeadAfter = countFainted(nextState.getTeam2View());
        if (theirDeadAfter > theirDeadBefore) reward += 0.3; // Nice chunk of reward
        
        int myDeadBefore = countFainted(state.getTeam1View());
        int myDeadAfter = countFainted(nextState.getTeam1View());
        if (myDeadAfter > myDeadBefore) reward -= 0.3; // Penalty
        
        return reward;
    }

    private double calculateDamageExchangeReward(final BattleView state, final BattleView nextState) {
        double reward = 0.0;
        
        // Damage Dealt (Positive)
        PokemonView theirPrev = state.getTeam2View().getActivePokemonView();
        PokemonView theirCurr = nextState.getTeam2View().getActivePokemonView();
        if (theirPrev != null && theirCurr != null && isSamePokemon(theirPrev, theirCurr)) {
            double hpPrev = (double) theirPrev.getCurrentStat(Stat.HP) / theirPrev.getInitialStat(Stat.HP);
            double hpCurr = (double) theirCurr.getCurrentStat(Stat.HP) / theirCurr.getInitialStat(Stat.HP);
            double diff = hpPrev - hpCurr;
            // Scale: If we did 50% damage, reward is 0.25
            if (diff > 0) reward += diff * 0.5; 
        }

        // Damage Taken (Negative)
        PokemonView myPrev = state.getTeam1View().getActivePokemonView();
        PokemonView myCurr = nextState.getTeam1View().getActivePokemonView();
        if (myPrev != null && myCurr != null && isSamePokemon(myPrev, myCurr)) {
            double hpPrev = (double) myPrev.getCurrentStat(Stat.HP) / myPrev.getInitialStat(Stat.HP);
            double hpCurr = (double) myCurr.getCurrentStat(Stat.HP) / myCurr.getInitialStat(Stat.HP);
            double diff = hpPrev - hpCurr;
            // Penalty is slightly smaller to encourage aggression (0.3 scale)
            if (diff > 0) reward -= diff * 0.3; 
        }
        return reward;
    }

    private double calculateStatusEffectReward(final BattleView state, final BattleView nextState) {
        double reward = 0.0;
        PokemonView theirPrev = state.getTeam2View().getActivePokemonView();
        PokemonView theirCurr = nextState.getTeam2View().getActivePokemonView();

        if (theirPrev != null && theirCurr != null && isSamePokemon(theirPrev, theirCurr)) {
            // New Status Inflicted?
            if (theirPrev.getNonVolatileStatus() == NonVolatileStatus.NONE && theirCurr.getNonVolatileStatus() != NonVolatileStatus.NONE) {
                if (theirCurr.getNonVolatileStatus() == NonVolatileStatus.SLEEP || theirCurr.getNonVolatileStatus() == NonVolatileStatus.FREEZE) {
                    reward += 0.25; // Massive advantage
                } else {
                    reward += 0.15; // Good advantage (Para/Psn/Burn)
                }
            }
        }
        return reward;
    }

    private double calculateStrategicMoveReward(final BattleView state, final MoveView action, final BattleView nextState) {
        // Small "cookie" for picking the right move type, helps early learning
        if (action == null || action.getPower() == null || action.getPower() <= 0) return 0.0;
        
        PokemonView enemy = state.getTeam2View().getActivePokemonView();
        if (enemy == null) return 0.0;

        double eff = Type.getEffectivenessModifier(action.getType(), enemy.getCurrentType1());
        if (enemy.getCurrentType2() != null) {
            eff *= Type.getEffectivenessModifier(action.getType(), enemy.getCurrentType2());
        }

        if (eff >= 2.0) return 0.1; // Small bonus
        return 0.0;
    }
    
    private double calculateMomentumReward(final BattleView state, final BattleView nextState) {
        // Did the overall health gap widen in our favor?
        double prevGap = calculateTeamHealthDifferential(state.getTeam1View(), state.getTeam2View());
        double nextGap = calculateTeamHealthDifferential(nextState.getTeam1View(), nextState.getTeam2View());
        
        double improvement = nextGap - prevGap;
        // Scale down significantly because this happens every turn
        return improvement * 0.1; 
    }

    // --- UTILS ---
    private int countFainted(TeamView team) {
        int count = 0;
        for (int i = 0; i < team.size(); i++) if (team.getPokemonView(i).hasFainted()) count++;
        return count;
    }

    private boolean isSamePokemon(PokemonView p1, PokemonView p2) {
        return p1.getName().equals(p2.getName());
    }
    
    private double calculateTeamHealthDifferential(TeamView myTeam, TeamView theirTeam) {
        double myTotalHealth = 0.0;
        double myMaxHealth = 0.0;
        double theirTotalHealth = 0.0;
        double theirMaxHealth = 0.0;
        
        for (int i = 0; i < myTeam.size(); i++) {
            PokemonView p = myTeam.getPokemonView(i);
            if (!p.hasFainted()) {
                myTotalHealth += p.getCurrentStat(Stat.HP);
                myMaxHealth += p.getInitialStat(Stat.HP);
            }
        }
        for (int i = 0; i < theirTeam.size(); i++) {
            PokemonView p = theirTeam.getPokemonView(i);
            if (!p.hasFainted()) {
                theirTotalHealth += p.getCurrentStat(Stat.HP);
                theirMaxHealth += p.getInitialStat(Stat.HP);
            }
        }
        
        double myRatio = (myMaxHealth > 0) ? myTotalHealth / myMaxHealth : 0.0;
        double theirRatio = (theirMaxHealth > 0) ? theirTotalHealth / theirMaxHealth : 0.0;
        
        return myRatio - theirRatio; // Range: -1.0 to 1.0
    }
}