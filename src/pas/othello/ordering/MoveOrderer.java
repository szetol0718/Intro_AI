package src.pas.othello.ordering;

// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;
import src.pas.othello.heuristics.Heuristics;

// JAVA PROJECT IMPORTS
import java.util.List;
import java.util.Collections;
import java.util.Comparator; // Import Comparator if not using lambda shorthand

public class MoveOrderer
    extends Object
{
    public static List<Node> orderChildren(List<Node> children)
    {
        // Sort children in DESCENDING order of their heuristic value (best moves first)
        Collections.sort(children, new Comparator<Node>() {
            @Override
            public int compare(Node n1, Node n2) {
                double h1 = Heuristics.calculateHeuristicValue(n1);
                double h2 = Heuristics.calculateHeuristicValue(n2);
                
                // h2 vs h1 ensures descending order (higher score first)
                int primaryCompare = Double.compare(h2, h1);

                if (primaryCompare != 0) {
                    return primaryCompare;
                }


                return Integer.compare(n1.hashCode(), n2.hashCode());
            }
        });
        
        return children;
    }
}