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



public class ZigZagAgent
    extends Agent
{

    // put your fields here! You will probably want to remember the following information:
    //      - your friendly unit id
    //      - the enemy unit id
    //      - the id of the gold
    private Integer myUnitId;            // our unit
    private Integer enemyUnitId;         // enemy unit
    private Integer goldResourceNodeId;  // gold deposit
    private boolean didFirstEastStep;    // to enforce E, N, E, N, ...
    private boolean nextIsNorth;         // after first EAST, alternate

    /**
     * The constructor for this type. The arguments (including the player number: id of the team we are controlling)
     * are contained within the game's xml file that we are running. We can also add extra arguments to the game's xml
     * config for this agent and those will be included in args.
     */
	public ZigZagAgent(int playerNum, String[] args)
	{
		super(playerNum); // make sure to call parent type (Agent)'s constructor!

        // initialize your fields here!
        this.myUnitId = null;
        this.enemyUnitId = null;
        this.goldResourceNodeId = null;
        this.didFirstEastStep = false;
        this.nextIsNorth = true; // after first EAST, the next step should be NORTH
        // helpful printout just to help debug
		System.out.println("Constructed ZigZagAgent");
	}

    /////////////////////////////// GETTERS AND SETTERS (this is Java after all) ///////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public final Integer getMyUnitId() { return this.myUnitId; }
    public final Integer getEnemyUnitId() { return this.enemyUnitId; }
    public final Integer getGoldResourceNodeId() { return this.goldResourceNodeId; }
    private void setMyUnitId(Integer i) { this.myUnitId = i; }
    private void setEnemyUnitId(Integer i) { this.enemyUnitId = i; }
    private void setGoldResourceNodeId(Integer i) { this.goldResourceNodeId = i; }


	@Override
	public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
	{
        // TODO: identify units, set fields, and then decide what to do
	    // Discover friendly unit(s)
        Set<Integer> myUnitIds = new HashSet<>();
        for (Integer uid : state.getUnitIds(this.getPlayerNumber())) {
            myUnitIds.add(uid);
        }

        // Find enemy player number
        Integer[] players = state.getPlayerNumbers();

        Integer enemyPlayerNum = players[0] != this.getPlayerNumber() ? players[0] : players[1];

        // Discover enemy unit(s)
        Set<Integer> enemyUnitIds = new HashSet<>();
        for (Integer uid : state.getUnitIds(enemyPlayerNum)) {
            enemyUnitIds.add(uid);
        }

        // Discover gold resource id
        Integer goldId = null;
        for (ResourceView rv : state.getAllResourceNodes()) {
            if (rv.getType() == ResourceNode.Type.GOLD_MINE) {
                goldId = rv.getID();
                break;
            }
        }

        // Set fields
        this.setMyUnitId(myUnitIds.iterator().next());
        this.setEnemyUnitId(enemyUnitIds.iterator().next());
        this.setGoldResourceNodeId(goldId);

        // Start the behavior
        return this.middleStep(state, history);
	}

	@Override
	public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        // TODO: your code to give your unit actions for this turn goes here!
        Integer myId = this.getMyUnitId();
        Integer enemyId = this.getEnemyUnitId();

        UnitView me = state.getUnit(myId);
        UnitView enemy = state.getUnit(enemyId);

        // If either unit is gone, no action (game likely over)
        if (me == null || enemy == null) {
            return actions;
        }

        int mx = me.getXPosition();
        int my = me.getYPosition();
        int ex = enemy.getXPosition();
        int ey = enemy.getYPosition();

        int dx = ex - mx;
        int dy = ey - my;

        // If adjacent (including diagonals), attack until enemy dies
        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
            actions.put(myId, Action.createPrimitiveAttack(myId, enemyId));
            return actions;
        }

        // Otherwise follow zig-zag: first EAST once, then alternate NORTH and EAST
        Direction moveDir;

        if (!didFirstEastStep) {
            moveDir = Direction.EAST;
            didFirstEastStep = true;
            nextIsNorth = true; // next turn go NORTH
        } else {
            moveDir = nextIsNorth ? Direction.NORTH : Direction.EAST;
            nextIsNorth = !nextIsNorth; // alternate each turn
        }

        actions.put(myId, Action.createPrimitiveMove(myId, moveDir));
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

