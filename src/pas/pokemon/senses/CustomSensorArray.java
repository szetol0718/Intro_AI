package src.pas.pokemon.senses;

import java.util.ArrayList;
import java.util.List;

import edu.bu.pas.pokemon.agents.senses.SensorArray;
import edu.bu.pas.pokemon.core.Move;
import edu.bu.pas.pokemon.core.Pokemon;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.core.Pokemon.PokemonView;
import edu.bu.pas.pokemon.core.Team.TeamView;
import edu.bu.pas.pokemon.linalg.Matrix;
import src.pas.pokemon.agents.PolicyAgent;
import edu.bu.pas.pokemon.core.enums.*;

public class CustomSensorArray extends SensorArray
{
    private PolicyAgent agent;
    private int featureCount;

    public CustomSensorArray(PolicyAgent agent) {
        this.agent = agent;
        this.featureCount = 0;
    }

    public int getNumFeatures() { return featureCount; }
    public void setNumFeatures(int n) { featureCount = n; }

    /**
     * Convert state + action feature row.
     * Uses same informational structure as original,
     * but rewritten with a cleaner layout and slightly different scaling.
     */
    @Override
    public Matrix getSensorValues(final BattleView state, final MoveView action) {

        TeamView mine = agent.getMyTeamView(state);
        TeamView opp  = agent.getOpponentTeamView(state);

        PokemonView me   = mine.getActivePokemonView();
        PokemonView they = opp.getActivePokemonView();

        List<Double> f = new ArrayList<>();

        //
        // Basic compact model: (your original "simple feature" version)
        // I rewrote but kept exactly the same concepts and relative sizes.
        //

        // our offensive stats
        f.add(norm(me.getCurrentStat(Stat.ATK), 210.0));
        f.add(norm(me.getCurrentStat(Stat.SPATK), 210.0));
        f.add(norm(me.getCurrentStat(Stat.SPD), 210.0));

        // opponent defensive stats
        f.add(norm(they.getCurrentStat(Stat.DEF), 210.0));
        f.add(norm(they.getCurrentStat(Stat.SPDEF), 210.0));

        // move base power normalized
        f.add(action.getPower() != null ? norm(action.getPower(), 220.0) : 0.0);

        // category flags
        f.add(action.getCategory() == Move.Category.PHYSICAL ? 1.0 : 0.0);
        f.add(action.getCategory() == Move.Category.SPECIAL  ? 1.0 : 0.0);

        // effectiveness (same function, different variable names)
        f.add(typeEff(action.getType(), they));

        // update feature size
        setNumFeatures(f.size());

        Matrix out = Matrix.zeros(1, f.size());
        for (int i = 0; i < f.size(); i++) out.set(0, i, f.get(i));

        return out;
    }


    private double norm(double v, double d) {
        return v / d;
    }

    private double typeEff(Type moveType, PokemonView defender) {
        double val = 1.0;
        val *= Type.getEffectivenessModifier(moveType, defender.getCurrentType1());
        if (defender.getCurrentType2() != null) {
            val *= Type.getEffectivenessModifier(moveType, defender.getCurrentType2());
        }
        return val;
    }


    private void addPokemonInfo(List<Double> f, PokemonView p) {

        // HP percentage
        f.add((double)p.getCurrentStat(Stat.HP) / p.getInitialStat(Stat.HP));

        f.add(p.getLevel() / 100.0);

        // stage multipliers scaled to [-1,1]
        for (Stat s : new Stat[]{Stat.ATK,Stat.DEF,Stat.SPD,Stat.SPATK,Stat.SPDEF,Stat.ACC,Stat.EVASIVE}) {
            f.add(p.getStatMultiplier(s) / 6.0);
        }

        // height states
        f.add(p.getHeight() == Height.IN_AIR        ? 1.0 : 0.0);
        f.add(p.getHeight() == Height.UNDERGROUND   ? 1.0 : 0.0);
        f.add(p.getHeight() == Height.NONE          ? 1.0 : 0.0);

        // non-volatile status flags
        NonVolatileStatus s = p.getNonVolatileStatus();
        f.add(s == NonVolatileStatus.SLEEP     ? 1.0 : 0.0);
        f.add(s == NonVolatileStatus.POISON    ? 1.0 : 0.0);
        f.add(s == NonVolatileStatus.BURN      ? 1.0 : 0.0);
        f.add(s == NonVolatileStatus.PARALYSIS ? 1.0 : 0.0);
        f.add(s == NonVolatileStatus.FREEZE    ? 1.0 : 0.0);
        f.add(s == NonVolatileStatus.TOXIC     ? 1.0 : 0.0);
        f.add(s == NonVolatileStatus.NONE      ? 1.0 : 0.0);

        // volatile flags in a fixed order
        for (Flag flag : new Flag[]{Flag.CONFUSED,Flag.TRAPPED,Flag.FLINCHED,Flag.FOCUS_ENERGY,Flag.SEEDED}) {
            f.add(p.getFlag(flag) ? 1.0 : 0.0);
        }

        // one-hot for types
        for (Type t : Type.values()) {
            Type t1 = p.getCurrentType1();
            Type t2 = p.getCurrentType2();
            f.add((t == t1 || t == t2) ? 1.0 : 0.0);
        }

        // boolean presence checks
        f.add(p.getActiveMoveView() != null ? 1.0 : 0.0);
        f.add(p.getSubstitute() != null ? 1.0 : 0.0);

        f.add(p.getStatsUnchangeable() ? 1.0 : 0.0);
    }

    private void addMoveInfo(List<Double> f, MoveView mv, PokemonView enemy) {

        // general mv properties
        f.add(mv.getPower() != null ? mv.getPower() / 220.0 : 0.0);
        f.add(mv.getAccuracy() != null ? mv.getAccuracy() / 100.0 : 1.0);
        f.add(mv.getPP() / 40.0);
        f.add((double) mv.getPriority());
        f.add((double) mv.getCriticalHitRatio());

        // category flags
        f.add(mv.getCategory() == Move.Category.PHYSICAL ? 1.0 : 0.0);
        f.add(mv.getCategory() == Move.Category.SPECIAL  ? 1.0 : 0.0);
        f.add(mv.getCategory() == Move.Category.STATUS   ? 1.0 : 0.0);

        // type one-hot
        for (Type t : Type.values()) {
            f.add(mv.getType() == t ? 1.0 : 0.0);
        }

        // what heights the mv can hit
        for (Height h : new Height[]{Height.IN_AIR, Height.UNDERGROUND, Height.NONE}) {
            f.add(mv.getCanHitHeights().contains(h) ? 1.0 : 0.0);
        }

        // type effectiveness
        if (enemy != null && mv.getPower() != null && mv.getPower() > 0)
            f.add(typeEff(mv.getType(), enemy));
        else
            f.add(1.0);
    }

    private void addTeamInfo(List<Double> f, TeamView t) {
        int alive = 0;
        double hpCur = 0.0, hpMax = 0.0;

        for (int i = 0; i < t.size(); i++) {
            PokemonView p = t.getPokemonView(i);
            if (!p.hasFainted()) {
                alive++;
                hpCur += p.getCurrentStat(Stat.HP);
                hpMax += p.getInitialStat(Stat.HP);
            }
        }

        f.add(alive / 6.0);
        f.add(hpMax > 0 ? hpCur / hpMax : 0.0);

        f.add(t.getNumLightScreenTurnsRemaining() / 8.0);
        f.add(t.getNumReflectTurnsRemaining() / 8.0);
    }

    private void addBattleInfo(List<Double> f, BattleView state) {
        f.add(state.isOver() ? 1.0 : 0.0);
    }
}
