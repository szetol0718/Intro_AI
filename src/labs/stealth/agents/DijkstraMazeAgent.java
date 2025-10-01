package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;                           // Directions in Sepia


import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue; // heap in java
import java.util.Set;


// JAVA PROJECT IMPORTS


public class DijkstraMazeAgent
    extends MazeAgent
{

    public DijkstraMazeAgent(int playerNum)
    {
        super(playerNum);
    }

@Override
public Path search(Vertex src, Vertex goal, StateView state) {
    final int X = state.getXExtent();
    final int Y = state.getYExtent();

    // Precompute resource blocks (trees, etc.)
    boolean[][] blocked = new boolean[X][Y];
    List<ResourceView> resources = state.getAllResourceNodes();
    if (resources != null) {
        for (ResourceView r : resources) {
            if (r != null) {
                int rx = r.getXPosition(), ry = r.getYPosition();
                if (0 <= rx && rx < X && 0 <= ry && ry < Y) blocked[rx][ry] = true;
            }
        }
    }

    // Dijkstra structures
    java.util.HashMap<Vertex, Float> dist = new java.util.HashMap<>();
    java.util.HashMap<Vertex, Vertex> parent = new java.util.HashMap<>();
    java.util.PriorityQueue<Vertex> pq = new java.util.PriorityQueue<>(
        (a, b) -> Float.compare(dist.get(a), dist.get(b))
    );

    dist.put(src, 0.0f);
    parent.put(src, null);
    pq.add(src);

    // 4-directional movement: up, down, left, right
    final int[][] OFF = { {0,-1}, {0,1}, {-1,0}, {1,0} };

    while (!pq.isEmpty()) {
        Vertex cur = pq.poll();
        if (cur.equals(goal)) break;

        int cx = cur.getXCoordinate(), cy = cur.getYCoordinate();
        float base = dist.get(cur);

        for (int i = 0; i < OFF.length; i++) {
            int nx = cx + OFF[i][0];
            int ny = cy + OFF[i][1];
            if (nx < 0 || nx >= X || ny < 0 || ny >= Y) continue;

            // allow stepping into goal even if otherwise occupied; only resources are walls
            if (!(nx == goal.getXCoordinate() && ny == goal.getYCoordinate()) && blocked[nx][ny]) continue;

            Vertex nb = new Vertex(nx, ny);
            float cand = base + 1.0f; // uniform edge cost

            Float old = dist.get(nb);
            if (old == null || cand < old) {
                dist.put(nb, cand);
                parent.put(nb, cur);
                pq.remove(nb); // safe even if absent
                pq.add(nb);
            }
        }
    }

    // No path found
    if (!dist.containsKey(goal)) return null;

    // Reconstruct src -> goal
    java.util.LinkedList<Vertex> chain = new java.util.LinkedList<>();
    for (Vertex v = goal; v != null; v = parent.get(v)) chain.addFirst(v);

    // Build Path ending at goal, with 1f per step
    Path path = new Path(chain.getFirst()); // base at src (zero length)
    for (int i = 1; i < chain.size(); i++) {
        path = new Path(chain.get(i), 1.0f, path);
    }
    return path;
}


}
