package src.labs.rttt.heuristics;


// SYSTEM IMPORTS
import edu.bu.labs.rttt.game.CellType;
import edu.bu.labs.rttt.game.PlayerType;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame;
import edu.bu.labs.rttt.game.RecursiveTicTacToeGame.RecursiveTicTacToeGameView;
import edu.bu.labs.rttt.traversal.Node;
import edu.bu.labs.rttt.utils.Coordinate;
import edu.bu.labs.rttt.utils.Pair;


// JAVA PROJECT IMPORTS



public class Heuristics
    extends Object
{

    public static int countNumberOfTicTacToeGamesWon(RecursiveTicTacToeGameView view,
                                                     PlayerType playerType)
    {
        int numGamesWon = 0;
        for(int rIdx = 0; rIdx < view.getNumRows(); ++rIdx)
        {
            for(int cIdx = 0; cIdx < view.getNumCols(); ++cIdx)
            {
                if(view.getOutcome(rIdx, cIdx) == playerType) { numGamesWon += 1; }
            }
        }
        return numGamesWon;
    }

    // measures local control within an unfinished small board
    private static double evaluateLocalBoard(RecursiveTicTacToeGameView view,
                                             PlayerType me,
                                             int boardRow, int boardCol) {
        double score = 0.0;
        PlayerType opp = (me == PlayerType.X) ? PlayerType.O : PlayerType.X;

        // skip boards already won
        if (view.getOutcome(boardRow, boardCol) != null) return 0;

        CellType[][] inner = view.getBoard(boardRow, boardCol);

        // mark control weighting
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (inner[r][c] == me.toCellType()) score += POSITION_WEIGHT;
                else if (inner[r][c] == opp.toCellType()) score -= POSITION_WEIGHT;
            }
        }

        // two-in-a-row checks
        int[][] lines = { {0,0,0,1,0,2}, {1,0,1,1,1,2}, {2,0,2,1,2,2},
                          {0,0,1,0,2,0}, {0,1,1,1,2,1}, {0,2,1,2,2,2},
                          {0,0,1,1,2,2}, {0,2,1,1,2,0} };

        for (int[] L : lines) {
            int myMarks = 0, oppMarks = 0, empties = 0;
            for (int i = 0; i < 3; i++) {
                int r = L[i*2], c = L[i*2+1];
                if (inner[r][c] == me.toCellType()) myMarks++;
                else if (inner[r][c] == opp.toCellType()) oppMarks++;
                else empties++;
            }
            if (myMarks == 2 && empties == 1) score += THREAT_WEIGHT;
            else if (oppMarks == 2 && empties == 1) score -= THREAT_WEIGHT;
        }

        return score;
    }

    // full heuristic combining macro and micro signals
    public static double calculateHeuristicValue(Node node) {
        RecursiveTicTacToeGameView view = node.getView();
        PlayerType me = node.getMyPlayerType();
        PlayerType opp = node.getOppositePlayerType();

        double score = 0.0;

        // macro: boards already won/lost
        score += WIN_WEIGHT * countBoardsWon(view, me);
        score -= WIN_WEIGHT * countBoardsWon(view, opp);

        // micro: potential control on unfinished boards
        for (int r = 0; r < view.getNumRows(); r++) {
            for (int c = 0; c < view.getNumCols(); c++) {
                score += evaluateLocalBoard(view, me, r, c);
            }
        }

        // keep inside (-100, +100)
        if (score > 99.0) score = 99.0;
        if (score < -99.0) score = -99.0;

        return score;
    }
}
