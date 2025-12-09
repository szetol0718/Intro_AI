package src.pas.pokemon.rewards;

import java.util.List;

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


public class CustomRewardFunction
    extends RewardFunction
{
    // keep same type (R(s,a,s'))
    public CustomRewardFunction()
    {
        super(RewardType.STATE_ACTION_STATE);
    }

    // keep same bounds
    @Override
    public double getLowerBound() { return -1000.0; }

    @Override
    public double getUpperBound() { return 1000.0; }

    @Override
    public double getStateReward(final BattleView state) { return 0d; }

    @Override
    public double getStateActionReward(final BattleView state,
                                       final MoveView action) { return 0d; }

    // main R(s,a,s') aggregator
    @Override
    public double getStateActionStateReward(final BattleView state,
                                            final MoveView action,
                                            final BattleView nextState)
    {
        double sum = 0.0;

        // game over outcome (big)
        sum += gameOutcomeDelta(state, nextState);

        // KOs (reward for KOing opponent, penalty for our KO)
        sum += koDelta(state, nextState);

        // damage traded: reward net damage efficiency
        sum += damageExchangeDelta(state, nextState);

        // status changes (non-volatile + volatile flags)
        sum += statusDelta(state, nextState);

        // stat stage changes
        sum += statStageDelta(state, nextState);

        // move strategy: type effectiveness, STAB, priority
        sum += strategicMoveDelta(state, action, nextState);

        // team/resource management: alive Pokemon, team HP
        sum += resourceMgmtDelta(state, nextState);

        // field control (height, screens)
        sum += fieldControlDelta(state, nextState);

        // momentum: progressive advantage between states
        sum += momentumDelta(state, nextState);

        // clamp to bounds and return
        return Math.max(getLowerBound(), Math.min(getUpperBound(), sum));
    }


    // big win/loss reward when game ends
    private double gameOutcomeDelta(final BattleView state, final BattleView nextState) {
        if (!nextState.isOver()) return 0.0;

        TeamView oppTeam = state.getTeam2View();
        boolean allOppFainted = true;
        for (int i = 0; i < oppTeam.size(); ++i) {
            if (!oppTeam.getPokemonView(i).hasFainted()) {
                allOppFainted = false;
                break;
            }
        }
        return allOppFainted ? 60.0 : -60.0; // same scale as original
    }

    // KO rewards: positive for KOing opponent, penalty for our KO (with clean-KO bonus)
    private double koDelta(final BattleView state, final BattleView nextState) {
        double r = 0.0;

        PokemonView oppNow = state.getTeam2View().getActivePokemonView();
        PokemonView oppNext = nextState.getTeam2View().getActivePokemonView();
        PokemonView meNow  = state.getTeam1View().getActivePokemonView();
        PokemonView meNext = nextState.getTeam1View().getActivePokemonView();

        // KO opponent
        if (oppNow != null && oppNext != null &&
            !oppNow.hasFainted() && oppNext.hasFainted()) {
            r += 15.0;

            // clean KO bonus: our remaining HP ratio high
            if (meNow != null && meNext != null) {
                double remain = (double) meNext.getCurrentStat(Stat.HP) / meNow.getInitialStat(Stat.HP);
                if (remain > 0.7) r += 8.0; // reward similar to original
            }
        }

        // we got KOed
        if (meNow != null && meNext != null &&
            !meNow.hasFainted() && meNext.hasFainted()) {
            r -= 35.0;
        }

        return r;
    }

    // net damage efficiency reward (deal more while taking less)
    private double damageExchangeDelta(final BattleView state, final BattleView nextState) {
        double r = 0.0;

        PokemonView myNow = state.getTeam1View().getActivePokemonView();
        PokemonView myNext = nextState.getTeam1View().getActivePokemonView();
        PokemonView oppNow = state.getTeam2View().getActivePokemonView();
        PokemonView oppNext = nextState.getTeam2View().getActivePokemonView();

        if (myNow != null && myNext != null && oppNow != null && oppNext != null) {
            int dealt = oppNow.getCurrentStat(Stat.HP) - oppNext.getCurrentStat(Stat.HP);
            int taken = myNow.getCurrentStat(Stat.HP) - myNext.getCurrentStat(Stat.HP);

            double dealtRatio = (double) dealt / Math.max(1, oppNow.getInitialStat(Stat.HP));
            double takenRatio = (double) taken / Math.max(1, myNow.getInitialStat(Stat.HP));

            double netEfficiency = dealtRatio - takenRatio;
            r += netEfficiency * 5.0; // same multiplier scale

            // bonus for heavy hitters who take little damage
            if (dealt > 0 && dealtRatio > 0.3) r += 2.0;
        }
        return r;
    }

    // evaluate status changes for both sides
    private double statusDelta(final BattleView state, final BattleView nextState) {
        double r = 0.0;
        PokemonView oppNow = state.getTeam2View().getActivePokemonView();
        PokemonView oppNext = nextState.getTeam2View().getActivePokemonView();
        PokemonView meNow  = state.getTeam1View().getActivePokemonView();
        PokemonView meNext = nextState.getTeam1View().getActivePokemonView();

        if (oppNow != null && oppNext != null) r += evalStatusChange(oppNow, oppNext, true);
        if (meNow != null && meNext != null) r += evalStatusChange(meNow, meNext, false);

        return r;
    }

    // status evaluation: non-volatile + volatile flags
    private double evalStatusChange(PokemonView before, PokemonView after, boolean isOpponent) {
        double r = 0.0;
        double sign = isOpponent ? 1.0 : -1.0; // improving opponent status -> positive for us

        NonVolatileStatus prev = before.getNonVolatileStatus();
        NonVolatileStatus next = after.getNonVolatileStatus();
        if (prev != next) {
            switch (next) {
                case SLEEP:
                case FREEZE:
                    r += 12.0 * sign; // huge control effect
                    break;
                case PARALYSIS:
                    r += 8.0 * sign;
                    break;
                case TOXIC:
                    r += 10.0 * sign;
                    break;
                case POISON:
                case BURN:
                    r += 6.0 * sign;
                    break;
                case NONE:
                    if (prev != NonVolatileStatus.NONE) r += -8.0 * sign;
                    break;
            }
        }

        // volatile flags: check each flag and reward/penalize accordingly
        for (Flag f : Flag.values()) {
            boolean had = before.getFlag(f);
            boolean has = after.getFlag(f);
            if (had != has) {
                switch (f) {
                    case CONFUSED:
                        r += 4.0 * sign;
                        break;
                    case TRAPPED:
                        r += 5.0 * sign;
                        break;
                    case SEEDED:
                        r += 6.0 * sign;
                        break;
                    case FLINCHED:
                        if (has) r += 3.0 * sign;
                        break;
                    case FOCUS_ENERGY:
                        if (has) r += -5.0 * sign;
                        break;
                    default:
                        break;
                }
            }
        }
        return r;
    }

    // stat stage changes: reward increases to our offense/defense and punish opponent buffs
    private double statStageDelta(final BattleView state, final BattleView nextState) {
        double r = 0.0;

        PokemonView meNow = state.getTeam1View().getActivePokemonView();
        PokemonView meNext = nextState.getTeam1View().getActivePokemonView();
        PokemonView oppNow = state.getTeam2View().getActivePokemonView();
        PokemonView oppNext = nextState.getTeam2View().getActivePokemonView();

        if (meNow != null && meNext != null) r += evalStatStages(meNow, meNext, false);
        if (oppNow != null && oppNext != null) r += evalStatStages(oppNow, oppNext, true);

        return r;
    }

    private double evalStatStages(PokemonView before, PokemonView after, boolean isOpponent) {
        double r = 0.0;
        double sign = isOpponent ? -1.0 : 1.0; // opponent stat drop => positive for us

        Stat[] stats = { Stat.ATK, Stat.DEF, Stat.SPD, Stat.SPATK, Stat.SPDEF };
        for (Stat s : stats) {
            int prevStage = before.getStatMultiplier(s);
            int nextStage = after.getStatMultiplier(s);
            int delta = nextStage - prevStage;
            if (delta != 0) {
                double val = delta * 3.0 * sign;
                if (s == Stat.ATK || s == Stat.SPATK) val *= 1.2;
                else if (s == Stat.SPD) val *= 1.5;
                r += val;
            }
        }
        return r;
    }

    // move strategy: type effectiveness, STAB, priority tuning
    private double strategicMoveDelta(final BattleView state, final MoveView action, final BattleView nextState) {
        double r = 0.0;
        if (action == null) return 0.0;

        PokemonView opp = state.getTeam2View().getActivePokemonView();
        PokemonView me  = state.getTeam1View().getActivePokemonView();
        if (opp == null || me == null) return 0.0;

        if (action.getPower() != null && action.getPower() > 0) {
            double eff = typeEff(action.getType(), opp.getCurrentType1(), opp.getCurrentType2());
            if (eff >= 4.0) r += 40.0;
            else if (eff >= 2.0) r += 30.0;
            else if (eff <= 0.5 && eff > 0.0) r -= 4.0;
            else if (eff == 0.0) r -= 10.0;
        }

        // STAB
        if (action.getPower() != null && action.getPower() > 0) {
            Type mt = action.getType();
            if (mt == me.getCurrentType1() || mt == me.getCurrentType2()) r += 3.0;
        }

        // Priority moves useful if we are slower or low HP
        if (action.getPriority() > 0) {
            int mySpd = me.getCurrentStat(Stat.SPD);
            int oppSpd = opp.getCurrentStat(Stat.SPD);
            double myHratio = (double) me.getCurrentStat(Stat.HP) / me.getInitialStat(Stat.HP);
            if (mySpd < oppSpd || myHratio < 0.3) r += 4.0;
        }

        return r;
    }

    // resource management: alive count and team HP differential
    private double resourceMgmtDelta(final BattleView state, final BattleView nextState) {
        double r = 0.0;
        TeamView myTeam = nextState.getTeam1View();
        TeamView theirTeam = nextState.getTeam2View();

        int myAlive = aliveCount(myTeam);
        int theirAlive = aliveCount(theirTeam);
        r += (myAlive - theirAlive) * 2.0;

        r += teamHealthDiff(myTeam, theirTeam);
        return r;
    }

    private int aliveCount(TeamView team) {
        int c = 0;
        for (int i = 0; i < team.size(); ++i) if (!team.getPokemonView(i).hasFainted()) c++;
        return c;
    }

    private double teamHealthDiff(TeamView mine, TeamView theirs) {
        double myTotal = 0.0, myMax = 0.0, theirTotal = 0.0, theirMax = 0.0;
        for (int i = 0; i < mine.size(); ++i) {
            PokemonView p = mine.getPokemonView(i);
            if (!p.hasFainted()) { myTotal += p.getCurrentStat(Stat.HP); myMax += p.getInitialStat(Stat.HP); }
        }
        for (int i = 0; i < theirs.size(); ++i) {
            PokemonView p = theirs.getPokemonView(i);
            if (!p.hasFainted()) { theirTotal += p.getCurrentStat(Stat.HP); theirMax += p.getInitialStat(Stat.HP); }
        }
        double myRatio = myMax > 0 ? myTotal / myMax : 0.0;
        double theirRatio = theirMax > 0 ? theirTotal / theirMax : 0.0;
        return (myRatio - theirRatio) * 20.0;
    }

    // field control: height advantage and screens
    private double fieldControlDelta(final BattleView state, final BattleView nextState) {
        double r = 0.0;
        PokemonView me = nextState.getTeam1View().getActivePokemonView();
        PokemonView opp = nextState.getTeam2View().getActivePokemonView();
        if (me != null && opp != null) {
            Height h1 = me.getHeight();
            Height h2 = opp.getHeight();
            if ((h1 == Height.IN_AIR || h1 == Height.UNDERGROUND) && h2 == Height.NONE) r += 2.0;
        }
        TeamView myTeam = nextState.getTeam1View();
        if (myTeam.getNumReflectTurnsRemaining() > 0) r += 1.0;
        if (myTeam.getNumLightScreenTurnsRemaining() > 0) r += 1.0;
        return r;
    }

    // momentum: change in overall advantage between current and next state
    private double momentumDelta(final BattleView state, final BattleView nextState) {
        double cur = evalBattle(state);
        double nxt = evalBattle(nextState);
        return (nxt - cur) * 2.0;
    }

    // small helper: compute a summary advantage value
    private double evalBattle(final BattleView s) {
        TeamView my = s.getTeam1View();
        TeamView their = s.getTeam2View();
        double score = 0.0;
        score += teamHealthDiff(my, their) / 2.0; // scaled like original evaluateBattleState
        score += (aliveCount(my) - aliveCount(their)) * 5.0;
        return score;
    }

    // type effectiveness helper (product of modifiers)
    private double typeEff(Type atk, Type def1, Type def2) {
        double eff = 1.0;
        eff *= Type.getEffectivenessModifier(atk, def1);
        if (def2 != null) eff *= Type.getEffectivenessModifier(atk, def2);
        return eff;
    }
}
