package src.pas.othello.agents;


// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

// JAVA PROJECT IMPORTS
import edu.bu.pas.othello.agents.Agent;
import edu.bu.pas.othello.agents.TimedTreeSearchAgent;
import edu.bu.pas.othello.game.Game.GameView;
import edu.bu.pas.othello.game.Game;
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;
import src.pas.othello.ordering.MoveOrderer;
import src.pas.othello.heuristics.Heuristics;

public class OthelloAgent
    extends TimedTreeSearchAgent
{

    public static class OthelloNode
        extends Node
    {
        public OthelloNode(final PlayerType maxPlayerType,  // who is MAX (me)
                           final GameView gameView,         // current state of the game
                           final int depth)                 // the depth of this node
        {
            super(maxPlayerType, gameView, depth);
        }

@Override
public double getTerminalUtility() {
    final double C = 100.0;

    int white = 0, black = 0;
    PlayerType[][] grid = getGameView().getCells();

    for (int r = 0; r < grid.length; r++) {
        for (int c = 0; c < grid[r].length; c++) {
            PlayerType t = grid[r][c];
            if (t == PlayerType.WHITE)      white++;
            else if (t == PlayerType.BLACK) black++;
        }
    }

    int sign = (getGameView().getCurrentPlayerType() == PlayerType.BLACK) ? -1 : 1;
    return (white == black) ? 0.0 : (C * sign);
}

    @Override
    public List<Node> getChildren() {
    final List<Node> out = new ArrayList<>();
    final PlayerType meNext = getOtherPlayerType();
    final Set<Coordinate> moves = getGameView().getFrontier(getCurrentPlayerType());

    // No legal action: pass turn (still produce a single child)
    if (moves.isEmpty()) {
        Game g = new Game(getGameView());
        g.setCurrentPlayerType(meNext);
        g.setTurnNumber(g.getTurnNumber() + 1);

        OthelloNode n = new OthelloNode(getMaxPlayerType(), g.getView(), getDepth() + 1);
        n.setLastMove(null);
        out.add(n);
        return out;
    }

    // Expand each legal move
    for (Coordinate m : moves) {
        Game g = new Game(getGameView());
        g.applyMove(m);
        g.setCurrentPlayerType(meNext);

        OthelloNode n = new OthelloNode(getMaxPlayerType(), g.getView(), getDepth() + 1);
        n.setLastMove(m);
        out.add(n);
    }

    return out;
}

    }

    private final Random random;

    public OthelloAgent(final PlayerType myPlayerType,
                        final long maxMoveThinkingTimeInMS)
    {
        super(myPlayerType,
              maxMoveThinkingTimeInMS);
        this.random = new Random();
    }

    public final Random getRandom() { return this.random; }

    @Override
    public OthelloNode makeRootNode(final GameView game)
    {
        // if you change OthelloNode's constructor, you will want to change this!
        // Note: I am starting the initial depth at 0 (because I like to count up)
        //       change this if you want to count depth differently
        return new OthelloNode(this.getMyPlayerType(), game, 0);
    }

    @Override
    public Node treeSearch(Node n)
    {
        // TODO: complete me!
        return null;
    }

    @Override
    public Coordinate chooseCoordinateToPlaceTile(final GameView game)
    {
        // TODO: this move will be called once per turn
        //       you may want to use this method to add to data structures and whatnot
        //       that your algorithm finds useful

        // make the root node
        Node node = this.makeRootNode(game);

        // call tree search
        Node moveNode = this.treeSearch(node);

        // return the move inside that node
        return moveNode.getLastMove();
    }

    @Override
    public void afterGameEnds(final GameView game) {}
}
