package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;

import java.util.ArrayDeque;
import java.util.HashSet;   // will need for dfs
import java.util.Stack;     // will need for dfs
import java.util.Set;       // will need for dfs


// JAVA PROJECT IMPORTS


public class DFSMazeAgent
    extends MazeAgent
{

    public DFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }

@Override
public Path search(Vertex src, Vertex goal, StateView state) {
    final int maxX = state.getXExtent();
    final int maxY = state.getYExtent();

    // Stack for DFS, visited set, and parent links for reconstruction
    ArrayDeque<Vertex> stack = new ArrayDeque<>();
    HashSet<Vertex> visited = new HashSet<>();
    java.util.HashMap<Vertex, Vertex> parent = new java.util.HashMap<>();

    stack.push(src);
    visited.add(src);
    parent.put(src, null);

    // 8-way neighbor offsets: N, NE, E, SE, S, SW, W, NW
    final int[][] OFF = {
        { 0,-1}, { 1,-1}, { 1, 0}, { 1, 1},
        { 0, 1}, {-1, 1}, {-1, 0}, {-1,-1}
    };

    // DFS loop
    while (!stack.isEmpty()) {
        Vertex cur = stack.pop();
        if (cur.equals(goal)) {
            // Reconstruct path src -> goal
            java.util.LinkedList<Vertex> verts = new java.util.LinkedList<>();
            for (Vertex v = goal; v != null; v = parent.get(v)) {
                verts.addFirst(v);
            }
            // Build Path that ends at goal; 1f per step
            Path path = new Path(verts.getFirst());
            for (int i = 1; i < verts.size(); i++) {
                path = new Path(verts.get(i), 1.0f, path);
            }
            return path;
        }

        int cx = cur.getXCoordinate();
        int cy = cur.getYCoordinate();

        // Expand neighbors (push order controls DFS traversal shape)
        for (int i = 0; i < OFF.length; i++) {
            int nx = cx + OFF[i][0];
            int ny = cy + OFF[i][1];

            if (!state.inBounds(nx, ny)) continue;

            Vertex nb = new Vertex(nx, ny);

            // Block resource tiles, but allow entering goal
            if (!nb.equals(goal) && state.isResourceAt(nx, ny)) continue;

            if (!visited.contains(nb)) {
                visited.add(nb);
                parent.put(nb, cur);
                stack.push(nb);
            }
        }
    }

    // No path found
    return null;
}


}
