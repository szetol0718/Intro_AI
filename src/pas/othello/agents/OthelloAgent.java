package src.pas.othello.agents;

// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

// JAVA PROJECT IMPORTS
import edu.bu.pas.othello.agents.TimedTreeSearchAgent;
import edu.bu.pas.othello.game.Game.GameView;
import edu.bu.pas.othello.game.Game;
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;
import edu.bu.pas.othello.utils.Coordinate;

import src.pas.othello.ordering.MoveOrderer;
import src.pas.othello.heuristics.Heuristics;

public class OthelloAgent extends TimedTreeSearchAgent {

   
    public static class OthelloNode extends Node {
        public OthelloNode(final PlayerType maxPlayerType,
                           final GameView gameView,
                           final int depth) {
            super(maxPlayerType, gameView, depth);
        }

        @Override
        public double getTerminalUtility()
        {
            double cValue = 100.0; // Symmetric interval [-c, +c] 
            int whiteCellCount = 0;
            int blackCellCount = 0;
    
            PlayerType[][] cells = getGameView().getCells();
            for (int i = 0; i < cells.length; i++) {
                for (int j = 0; j < cells[i].length; j++) {
                    PlayerType cell = cells[i][j];
                    if (cell == PlayerType.BLACK) {
                        blackCellCount++;
                    } else if (cell == PlayerType.WHITE) {
                     whiteCellCount++;
                    }
                }
            }   

            // Determine utility from the perspective of the MAX player (me)
            PlayerType myPlayer = getMaxPlayerType();
            int myPieceCount;
            int opponentPieceCount;

            if (myPlayer == PlayerType.WHITE) {
                myPieceCount = whiteCellCount;
                opponentPieceCount = blackCellCount;
            } else {
                myPieceCount = blackCellCount;
                opponentPieceCount = whiteCellCount;
            }

            // Return +c if I win, -c if I lose, 0 for a tie
            if (myPieceCount > opponentPieceCount) {
                return cValue;
            } else if (opponentPieceCount > myPieceCount) {
                return -cValue;
            } else {
                return 0.0; // Tie 
            }
        }

        @Override
        public List<Node> getChildren() {
            List<Node> children = new ArrayList<>();
            Set<Coordinate> legal = getGameView().getFrontier(getCurrentPlayerType());
            PlayerType other = getOtherPlayerType();

            if (!legal.isEmpty()) {
                for (Coordinate mv : legal) {
                    Game g = new Game(getGameView());
                    g.applyMove(mv);
                    g.setCurrentPlayerType(other);
                    OthelloNode ch = new OthelloNode(getMaxPlayerType(), g.getView(), getDepth() + 1);
                    ch.setLastMove(mv);
                    children.add(ch);
                }
            } else {
                // pass
                Game g = new Game(getGameView());
                g.setCurrentPlayerType(other);
                g.setTurnNumber(g.getTurnNumber() + 1);
                OthelloNode ch = new OthelloNode(getMaxPlayerType(), g.getView(), getDepth() + 1);
                ch.setLastMove(null);
                children.add(ch);
            }
            return children;
        }
    }

    
    private final Random random;

    public OthelloAgent(final PlayerType myPlayerType,
                        final long maxMoveThinkingTimeInMS) {
        super(myPlayerType, maxMoveThinkingTimeInMS);
        this.random = new Random();
    }

    public final Random getRandom() { return this.random; }

    @Override
    public OthelloNode makeRootNode(final GameView game) {
        return new OthelloNode(this.getMyPlayerType(), game, 0);
    }

@Override
    public Node treeSearch(Node n)
    {
        // The main treeSearch method is responsible for finding the *best child* of the root
        // and returning that child node.
        
        int maxSearchDepth = 2; // Set a more reasonable depth
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        Node bestMoveNode = null;
        double maxUtility = Double.NEGATIVE_INFINITY;

        // The root node is always a maximizing player for our agent
        // We manually loop through the root's children
        List<Node> children = MoveOrderer.orderChildren(n.getChildren());

        if (children.isEmpty()) {
            // This can happen if we have no legal moves at the root
            n.setLastMove(null); // Set the move to null
            return n; // Return the root node itself with a null move [cite: 107]
        }

        for (Node child : children) {
            // Call the recursive helper on each child
            // The child is a minimizer node, so maximizingPlayer=false
            double childUtility = alphaBetaPruning(child, maxSearchDepth - 1, alpha, beta, false);

            if (childUtility > maxUtility) {
                maxUtility = childUtility;
                bestMoveNode = child;
            }

            // Update alpha for the root
            alpha = Math.max(alpha, maxUtility);
        }

        // Return the child node that leads to the best utility
        return bestMoveNode;
    }

    
    /**
     * Recursive helper for alpha-beta pruning.
     * This method returns the *utility value* (as a double) of the given node.
     */
    public double alphaBetaPruning(Node node, int depth, double alpha, double beta, boolean maximizingPlayer) {
        // Terminal case: depth limit reached or game is over
        if (depth == 0 || node.isTerminal()) {
            double utility;
            if (node.isTerminal()) {
                utility = node.getTerminalUtility();
            } else {
                // We are at the depth limit, use the heuristic [cite: 76]
                utility = Heuristics.calculateHeuristicValue(node);
            }
            node.setUtilityValue(utility); // Store the utility
            return utility;
        }

        // Recursive case
        if (maximizingPlayer) {
            double maxValue = Double.NEGATIVE_INFINITY;
            List<Node> children = MoveOrderer.orderChildren(node.getChildren());

            for (Node child : children) {
                double value = alphaBetaPruning(child, depth - 1, alpha, beta, false); // Recursive call
                maxValue = Math.max(maxValue, value);
                alpha = Math.max(alpha, maxValue);
                if (beta <= alpha) { // Pruning
                    break;
                }
            }
            node.setUtilityValue(maxValue);
            return maxValue;

        } else { // Minimizing player
            double minValue = Double.POSITIVE_INFINITY;
            List<Node> children = MoveOrderer.orderChildren(node.getChildren());

            for (Node child : children) {
                double value = alphaBetaPruning(child, depth - 1, alpha, beta, true); 
                minValue = Math.min(minValue, value);
                beta = Math.min(beta, minValue);
                if (beta <= alpha) { // Pruning
                    break;
                }
            }
            node.setUtilityValue(minValue);
            return minValue;
        }
    }


    @Override
    public Coordinate chooseCoordinateToPlaceTile(final GameView game) {
        Node root = this.makeRootNode(game);
        Node best = this.treeSearch(root);
        return best.getLastMove();
    }

    @Override
    public void afterGameEnds(final GameView game) {}
}
