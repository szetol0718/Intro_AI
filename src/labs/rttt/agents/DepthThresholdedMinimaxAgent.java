package src.labs.rttt.agents;


// SYSTEM IMPORTS
import edu.bu.labs.rttt.agents.Agent;
import edu.bu.labs.rttt.game.CellType;
import edu.bu.labs.rttt.game.PlayerType;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame.RecursiveTicTacToeGameView;
import edu.bu.labs.rttt.traversal.Node;
import edu.bu.labs.rttt.utils.Coordinate;
import edu.bu.labs.rttt.utils.Pair;

import java.util.List;
import java.util.Map;


// JAVA PROJECT IMPORTS
import src.labs.rttt.heuristics.Heuristics;


public class DepthThresholdedMinimaxAgent
    extends Agent
{

    public static final int DEFAULT_MAX_DEPTH = 3;

    private int maxDepth;

    public DepthThresholdedMinimaxAgent(PlayerType myPlayerType)
    {
        super(myPlayerType);
        this.maxDepth = DEFAULT_MAX_DEPTH;
    }

    public final int getMaxDepth() { return this.maxDepth; }
    public void setMaxDepth(int i) { this.maxDepth = i; }

    public String getTabs(Node node)
    {
        StringBuilder b = new StringBuilder();
        for(int idx = 0; idx < node.getDepth(); ++idx)
        {
            b.append("\t");
        }
        return b.toString();
    }

public Node minimax(Node node)
{
    // // uncomment if you want to see the tree being made
    // System.out.println(this.getTabs(node) + "Node(currentPlayer=" + node.getCurrentPlayerType() +
    //      " isTerminal=" + node.isTerminal() + " lastMove=" + node.getLastMove() + ")");

    // terminal node case
    if (node.isTerminal()) {
        node.setUtilityValue(node.getTerminalUtility());
        return node;
    }

    // cutoff case(depth limit)
    if (node.getDepth() >= this.getMaxDepth()) {
        node.setUtilityValue(Heuristics.calculateHeuristicValue(node));
        return node;
    }

    List<Node> children = node.getChildren();
    if (children.isEmpty()) {
        node.setUtilityValue(Heuristics.calculateHeuristicValue(node));
        return node;
    }

    boolean maximizing = (node.getCurrentPlayerType() == node.getMyPlayerType());
    double bestVal = maximizing ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    Node best = null;

    for (Node child : children) {
        Node eval = minimax(child);
        double score = eval.getUtilityValue();

        if (maximizing && score > bestVal) {
            bestVal = score;
            best = child;
        } else if (!maximizing && score < bestVal) {
            bestVal = score;
            best = child;
        }
    }

    node.setUtilityValue(bestVal);
    return best;
}


    @Override
    public Pair<Coordinate, Coordinate> makeFirstMove(final RecursiveTicTacToeGameView game)
    {
        // the first move has two choices we need to make:
        //      (1) which small board do we want to play on?
        //      (2) what square in the small board to we want to mark?
        // we'll solve this by iterating over all options for decision (1) and using minimax over all options for (2).
        // we'll pick the answer to (1) which leads to the best utility amongst all options for (1)
        // and choose the move which optimizes the choice for (1) to decide (2)
        Coordinate bestOuterBoardChoice = null;
        Double bestOuterUtility = null;
        Coordinate bestInnerBoardChoice = null;
        for(Coordinate potentialOuterBoardChoice : game.getAvailableFirstMoves().keySet())
        {
            // now that we have a choice for (1) we need to convey that to the game
            // so we'll make a RecursiveTicTacToeGame object which is mutable and set
            // the current game to the potentialOuterBoardChoice
            // then we can search like normal
            RecursiveTicTacToeGame gameToSetCurrentGame = new RecursiveTicTacToeGame(game);
            gameToSetCurrentGame.setCurrentGameCoord(potentialOuterBoardChoice);

            Node innerChoiceNode = this.minimax(new Node(gameToSetCurrentGame.getView(), this.getMyPlayerType(), 0));

            if(bestOuterUtility == null || (innerChoiceNode.getUtilityValue() > bestOuterUtility))
            {
                bestOuterBoardChoice = potentialOuterBoardChoice;
                bestOuterUtility = innerChoiceNode.getUtilityValue();
                bestInnerBoardChoice = innerChoiceNode.getLastMove();   // get the move that lead to this node
            }
        }

        return new Pair<Coordinate, Coordinate>(bestOuterBoardChoice, bestInnerBoardChoice);
    }

    @Override
    public Coordinate makeOtherMove(final RecursiveTicTacToeGameView game)
    {
        Node bestInnerChoiceNode = this.minimax(new Node(game, this.getMyPlayerType(), 0));
        return bestInnerChoiceNode.getLastMove();
    }

    @Override
    public void afterGameEnds(final RecursiveTicTacToeGameView game) {}
}
