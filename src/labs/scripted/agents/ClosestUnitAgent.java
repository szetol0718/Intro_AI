package src.labs.scripted.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;                                        // how we tell sepia what each unit will do
import edu.cwru.sepia.agent.Agent;                                          // base class for an Agent in sepia
import edu.cwru.sepia.environment.model.history.History.HistoryView;        // history of the game so far
import edu.cwru.sepia.environment.model.state.ResourceNode;                 // tree or gold
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;    // the "state" of that resource
import edu.cwru.sepia.environment.model.state.ResourceType;                 // what kind of resource units are carrying
import edu.cwru.sepia.environment.model.state.State.StateView;              // current state of the game
import edu.cwru.sepia.environment.model.state.Unit.UnitView;                // current state of a unit
import edu.cwru.sepia.util.Direction;                                       // directions for moving in the map


import java.io.InputStream;
import java.io.OutputStream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS



public class ClosestUnitAgent
    extends Agent
{

    // put your fields here! You will probably want to remember the following information:
    //      - all friendly unit ids (there may be more than one!)
    //      - the enemy unit id
    //      - the id of the gold
    private Set<Integer> myUnitIds;         // multiple friendly units
    private Integer enemyUnitId;            // single enemy unit
    private Integer goldResourceNodeId;     // gold deposit (optional for this task)

    /**
     * The constructor for this type. The arguments (including the player number: id of the team we are controlling)
     * are contained within the game's xml file that we are running. We can also add extra arguments to the game's xml
     * config for this agent and those will be included in args.
     */
	public ClosestUnitAgent(int playerNum, String[] args)
	{
		super(playerNum); // make sure to call parent type (Agent)'s constructor!

        // initialize your fields here!
        this.myUnitIds = new HashSet<>();
        this.enemyUnitId = null;
        this.goldResourceNodeId = null;
        // helpful printout just to help debug
		System.out.println("Constructed ClosestUnitAgent");
	}

    /////////////////////////////// GETTERS AND SETTERS (this is Java after all) ///////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public Set<Integer> getMyUnitIds() { return this.myUnitIds; }
    public Integer getEnemyUnitId() { return this.enemyUnitId; }
    public Integer getGoldResourceNodeId() { return this.goldResourceNodeId; }

    private void setEnemyUnitId(Integer i) { this.enemyUnitId = i; }
    private void setGoldResourceNodeId(Integer i) { this.goldResourceNodeId = i; }


	@Override
	public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
	{
        // TODO: identify units, set fields, and then decide what to do
        this.myUnitIds.clear();
        for (Integer uid : state.getUnitIds(this.getPlayerNumber())) {
            this.myUnitIds.add(uid);
        }
        Integer[] players = state.getPlayerNumbers();
        Integer enemyPlayerNum = players[0] != this.getPlayerNumber() ? players[0] : players[1];
        // Discover enemy unit(s) — lab maps use exactly one enemy unit
        Set<Integer> enemyIds = new HashSet<>();
        for (Integer uid : state.getUnitIds(enemyPlayerNum)) {
            enemyIds.add(uid);
        }
        this.setEnemyUnitId(enemyIds.iterator().next());
		// Discover gold resource id (not strictly required for Task 12, but included for parity)
        Integer goldId = null;
        for (ResourceView rv : state.getAllResourceNodes()) {
            if (rv.getType() == ResourceNode.Type.GOLD_MINE) {
                goldId = rv.getID();
                break;
            }
        }
        this.setGoldResourceNodeId(goldId);

        return this.middleStep(state, history);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        // TODO: your code to give your unit actions for this turn goes here!
        // If enemy gone, nothing to do
        Integer enemyId = this.getEnemyUnitId();
        UnitView enemy = enemyId == null ? null : state.getUnit(enemyId);
        if (enemy == null) {
            return actions;
        }

        // Select the closest friendly to the enemy (Euclidean distance)
        Integer chosenId = null;
        double bestDist = Double.POSITIVE_INFINITY;
        int ex = enemy.getXPosition();
        int ey = enemy.getYPosition();

        for (Integer uid : this.myUnitIds) {
            UnitView u = state.getUnit(uid);
            if (u == null) continue; // dead
            int ux = u.getXPosition();
            int uy = u.getYPosition();
            double dist = Math.hypot(ux - ex, uy - ey); // sqrt((dx)^2 + (dy)^2)
            if (dist < bestDist) {
                bestDist = dist;
                chosenId = uid;
            }
        }

        if (chosenId == null) {
            // all units dead?
            return actions;
        }

        UnitView me = state.getUnit(chosenId);
        int mx = me.getXPosition();
        int my = me.getYPosition();

        int dx = ex - mx;
        int dy = ey - my;

        // If adjacent (including diagonals), attack
        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
            actions.put(chosenId, Action.createPrimitiveAttack(chosenId, enemyId));
            return actions;
        }

        // Movement policy:
        // 1) Move along x-axis toward enemy until mx == ex
        // 2) Then move along y-axis toward enemy until adjacent
        Direction moveDir;
        if (dx != 0) {
            moveDir = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            moveDir = dy > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        actions.put(chosenId, Action.createPrimitiveMove(chosenId, moveDir));
        return actions;
	}

    @Override
	public void terminalStep(StateView state,
                             HistoryView history)
    {
        // don't need to do anything
    }

    /**
     * The following two methods aren't really used by us much in this class. These methods are used to load/save
     * the Agent (for instance if our Agent "learned" during the game we might want to save the model, etc.). Until the
     * very end of this class we will ignore these two methods.
     */
    @Override
	public void loadPlayerData(InputStream is) {}

	@Override
	public void savePlayerData(OutputStream os) {}

}

