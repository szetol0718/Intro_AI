package src.pas.othello.heuristics;

// SYSTEM IMPORTS
import edu.bu.pas.othello.game.PlayerType;
import edu.bu.pas.othello.traversal.Node;

// JAVA PROJECT IMPORTS
import java.util.HashMap;
import java.util.Map;

public class Heuristics extends Object {

    // Used for heuristic lookup (using the provided key type)
    private static Map<Long, Double> cachedHeuristics = new HashMap<>(); 

    public static double calculateHeuristicValue(Node node) {
        // Use node.hashCode() as the cache key, as in the original code
        long key = node.hashCode(); 
        if (cachedHeuristics.containsKey(key)) {
            return cachedHeuristics.get(key);
        } else {
            double value = getHeuristicVal(node);
            cachedHeuristics.put(key, value);
            return value;
        }
    }

    // Positional weight graph (kept the same)
    private static final int[][] WEIGHTED_GRAPH = {
        {100, -30,  15,   10,   10,  15, -30, 100},
        {-30, -60,  -5,  -5,  -5,  -5, -60, -30},
        { 15,  -5,  15,   3,   3,  15,  -5,  15},
        {  10,  -5,   3,   3,   3,   3,  -5,   10},
        {  10,  -5,   3,   3,   3,   3,  -5,   10},
        { 15,  -5,  15,   3,   3,  15,  -5,  15},
        {-30, -60,  -5,  -5,  -5,  -5, -60, -30},
        {100, -30,  15,   10,   10,  15, -30, 100}
    };
    public static double getHeuristicVal(Node node) {
        final PlayerType myType = node.getCurrentPlayerType();
        final PlayerType opponentType = node.getOtherPlayerType();
        final PlayerType[][] board = node.getGameView().getCells();
    
        int myPieceCount = 0;
        int opponentPieceCount = 0;
        int positionValue = 0;
        int myCorners = 0;
        int opponentCorners = 0;

        // Calculate Piece Count and Positional Score in a single 8x8 pass
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                final PlayerType cell = board[r][c];
            
                if (cell == myType) {
                    myPieceCount += 1;
                    positionValue += WEIGHTED_GRAPH[r][c];
                } else if (cell == opponentType) {
                    opponentPieceCount += 1;
                    positionValue -= WEIGHTED_GRAPH[r][c];
                }

                // Corner Check: Check corner ownership inside the main loop
                if ((r == 0 || r == 7) && (c == 0 || c == 7)) {
                    if (cell == myType) myCorners++;
                    else if (cell == opponentType) opponentCorners++;
                }
            }
        }
    
        final int totalPieceCount = myPieceCount + opponentPieceCount;
        final int pieceDifference = myPieceCount - opponentPieceCount;
        final int cornerDifference = myCorners - opponentCorners;

        // Get Mobility from the pre-calculated frontier (fast operation)
        final int myNumMoves = node.getGameView().getFrontier(myType).size(); 
        final int opponentNumMoves = node.getGameView().getFrontier(opponentType).size(); 
        final int frontierDifference = myNumMoves - opponentNumMoves;
    
        // Convert to double to maximize resolution
        double primaryScore;

        // Early game: Focus on Mobility and Corner Control
        if (totalPieceCount < 20) {
            primaryScore = (double)(
                frontierDifference * 8 +
                pieceDifference * 0 +
                cornerDifference * 100
            );
        }
        // Middle game: Balance Mobility, Pieces, and Position
        else if (totalPieceCount >= 20 && totalPieceCount <= 50) {
            primaryScore = (double)(
                frontierDifference * 6 +
                pieceDifference * 1 +
                cornerDifference * 100
            );
        }
        // End game: Focus purely on Piece Count and Corners
        else {
            primaryScore = (double)(
                frontierDifference * 0 +
                pieceDifference * 100 +
                cornerDifference * 100
            );
        }
    

        return primaryScore + ((double)positionValue / 10000.0);
    }
}