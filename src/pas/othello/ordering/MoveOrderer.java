package src.pas.othello.ordering;

// SYSTEM IMPORTS
import edu.bu.pas.othello.traversal.Node;
import java.util.Collections;
import java.util.List;

// JAVA PROJECT IMPORTS


public class MoveOrderer {

    /**
     * Orders the list of child nodes before search expansion.
     * Currently, this implementation simply randomizes the list
     * to avoid deterministic expansion order.
     *
     * @param children the list of child nodes to reorder
     * @return the reordered (shuffled) list of nodes
     */
    public static List<Node> orderChildren(List<Node> children) {

        Collections.shuffle(children);
        return children;
    }
}
