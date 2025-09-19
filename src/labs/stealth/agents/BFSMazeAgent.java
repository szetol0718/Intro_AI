package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.List;
import java.util.Set;           // will need for bfs


// JAVA PROJECT IMPORTS


public class BFSMazeAgent
    extends MazeAgent
{

    public BFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }
@Override
public Path search(Vertex src, Vertex goal, StateView state) {
    // Grid size
    final int maxX = state.getXExtent();
    final int maxY = state.getYExtent();

    // BFS work sets
    Queue<Vertex> q = new ArrayDeque<>();
    Set<Vertex> seen = new HashSet<>();
    java.util.HashMap<Vertex, Vertex> prev = new java.util.HashMap<>();

    // Seed
    q.add(src);
    seen.add(src);
    prev.put(src, null);

    // 8-directional offsets: N, NE, E, SE, S, SW, W, NW
    final int[][] OFF = {
        { 0,-1}, { 1,-1}, { 1, 0}, { 1, 1},
        { 0, 1}, {-1, 1}, {-1, 0}, {-1,-1}
    };

    boolean reached = false;

    // BFS
    while (!q.isEmpty() && !reached) {
        Vertex cur = q.poll();
        int cx = cur.getXCoordinate();
        int cy = cur.getYCoordinate();

        // Expand neighbors
        for (int i = 0; i < OFF.length; i++) {
            int nx = cx + OFF[i][0];
            int ny = cy + OFF[i][1];

            if (!state.inBounds(nx, ny)) continue;

            Vertex nb = new Vertex(nx, ny);

            // Skip blocked resource tiles (but allow goal regardless)
            if (!nb.equals(goal) && state.isResourceAt(nx, ny)) continue;

            if (!seen.contains(nb)) {
                seen.add(nb);
                prev.put(nb, cur);
                q.add(nb);

                if (nb.equals(goal)) {
                    reached = true;
                    break;
                }
            }
        }
    }

    // No route
    if (!reached) return null;

    // Rebuild vertex chain src -> goal
    java.util.LinkedList<Vertex> verts = new java.util.LinkedList<>();
    for (Vertex v = goal; v != null; v = prev.get(v)) {
        verts.addFirst(v);
    }

    // Build Path (reverse singly-linked), base at src then extend with 1f per step
    Path path = new Path(verts.getFirst());
    for (int i = 1; i < verts.size(); i++) {
        path = new Path(verts.get(i), 1.0f, path);
    }
    return path;
}

}
