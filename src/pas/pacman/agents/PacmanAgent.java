package src.pas.pacman.agents;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
// SYSTEM IMPORTS
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.Iterator;


// JAVA PROJECT IMPORTS
import edu.bu.pas.pacman.agents.Agent;
import edu.bu.pas.pacman.agents.SearchAgent;
import edu.bu.pas.pacman.interfaces.ThriftyPelletEater;
import edu.bu.pas.pacman.game.Action;
import edu.bu.pas.pacman.game.Game.GameView;
import edu.bu.pas.pacman.graph.Path;
import edu.bu.pas.pacman.graph.PelletGraph.PelletVertex;
import edu.bu.pas.pacman.utils.Coordinate;
import edu.bu.pas.pacman.utils.Pair;


public class PacmanAgent
    extends SearchAgent
    implements ThriftyPelletEater
{

    private final Random random;
    // Cache for pairwise distances between coordinates (used by getEdgeWeight)
    private final Map<String, Float> distanceCache = new HashMap<>();

    // Path and iterators for pellet-level planning
    private Path<PelletVertex> currentPelletPath = null;
    private Iterator<PelletVertex> pelletPathIterator = null;
    private PelletVertex currentTargetVertex = null;

    //  Risk / Ghost avoidance
    private static final float GHOST_RISK_WEIGHT = 0.8f; // how strong ghost avoidance is
    private static final int GHOST_RADIUS = 6;            // how far the danger spreads

    // cache ghost distances (updated each move)
    private Map<Coordinate, Integer> ghostDistances = new HashMap<>();


    public PacmanAgent(int myUnitId,
                       int pacmanId,
                       int ghostChaseRadius)
    {
        super(myUnitId, pacmanId, ghostChaseRadius);
        this.random = new Random();
    }

    public final Random getRandom() { return this.random; }

    @Override
    public Set<PelletVertex> getOutoingNeighbors(final PelletVertex current, final GameView view) {
    // Returns all states reachable by consuming exactly one additional pellet.
    Set<PelletVertex> results = new HashSet<>();

    for (Coordinate pellet : current.getRemainingPelletCoordinates()) {
        // Removing this pellet yields a new vertex (state)
        PelletVertex nextState = current.removePellet(pellet);
        results.add(nextState);
    }

    return results;
}


    @Override
    public float getEdgeWeight(final PelletVertex from, final PelletVertex to) {
    String key = formatCacheKey(from.getPacmanCoordinate(), to.getPacmanCoordinate());

    Float known = distanceCache.get(key);
    if (known != null) {
        return known;
    }

    // No cached distance — treat as unresolved until computed during search
    return Float.POSITIVE_INFINITY;
}

// Utility: creates symmetric key for cache lookups
    private String formatCacheKey(Coordinate a, Coordinate b) {
    int ax = a.getXCoordinate(), ay = a.getYCoordinate();
    int bx = b.getXCoordinate(), by = b.getYCoordinate();

    // Order coordinates lexicographically to maintain symmetry
    if (ax < bx || (ax == bx && ay <= by)) {
        return ax + "," + ay + ":" + bx + "," + by;
    } else {
        return bx + "," + by + ":" + ax + "," + ay;
    }
}


    @Override
    public float getHeuristic(final PelletVertex state, final GameView view) {
    float pellets = state.getRemainingPelletCoordinates().size();
    Coordinate pac = state.getPacmanCoordinate();
    float ghostPenalty = ghostRisk(pac) * 2f; // small extra fear near ghosts
    return pellets + ghostPenalty;
}



    @Override
    public Path<PelletVertex> findPathToEatAllPelletsTheFastest(final GameView view) {
    PriorityQueue<SearchNode> openSet = new PriorityQueue<>();
    Map<String, SearchNode> bestSeen = new HashMap<>();
    Set<String> closedSet = new HashSet<>();

    // Start node initialization
    SearchNode start = new SearchNode(new PelletVertex(view));
    start.gCost = 0f;
    start.hCost = getHeuristic(start.vertex, view);

    openSet.add(start);
    bestSeen.put(start.stateKey, start);

    while (!openSet.isEmpty()) {
        SearchNode current = openSet.poll();
        if (current == null) break;

        // Goal test: all pellets consumed
        if (current.vertex.getRemainingPelletCoordinates().isEmpty()) {
            return buildPath(current);
        }

        closedSet.add(current.stateKey);

        // Explore all possible next states (removing one pellet)
        for (PelletVertex successor : getOutoingNeighbors(current.vertex, view)) {
            SearchNode next = new SearchNode(successor);
            if (closedSet.contains(next.stateKey)) continue;

            // Retrieve distance between these two Pacman positions
            float dist = getEdgeWeight(current.vertex, successor);
            if (Float.isInfinite(dist)) {
                // Not cached yet — compute via standard graph search and store
                Path<Coordinate> subpath =
                    graphSearch(current.vertex.getPacmanCoordinate(),
                                successor.getPacmanCoordinate(),
                                view);
                dist = subpath.getTrueCost();
                distanceCache.put(formatCacheKey(
                    current.vertex.getPacmanCoordinate(),
                    successor.getPacmanCoordinate()), dist);
            }

            float tentativeG = current.gCost + dist;
            SearchNode recorded = bestSeen.get(next.stateKey);

            if (recorded == null || tentativeG < recorded.gCost) {
                next.parent = current;
                next.gCost = tentativeG;
                next.hCost = getHeuristic(successor, view);
                openSet.add(next);
                bestSeen.put(next.stateKey, next);
            }
        }
    }

    // No complete path found
    return null;
}
// Inner class used for A* search across pellet states
private static class SearchNode implements Comparable<SearchNode> {
    final PelletVertex vertex;
    SearchNode parent;
    float gCost;
    float hCost;
    final String stateKey;

    SearchNode(PelletVertex vertex) {
        this.vertex = vertex;
        this.stateKey = buildStateKey(vertex);
    }

    float fCost() { return gCost + hCost; }

    @Override
    public int compareTo(SearchNode other) {
        return Float.compare(this.fCost(), other.fCost());
    }

    // Generate a unique state key (Pacman position + sorted remaining pellets)
    private static String buildStateKey(PelletVertex v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getPacmanCoordinate().getXCoordinate())
          .append(',')
          .append(v.getPacmanCoordinate().getYCoordinate())
          .append(':');
        List<Coordinate> pellets = new ArrayList<>(v.getRemainingPelletCoordinates());
        pellets.sort(Comparator.comparingInt(Coordinate::getXCoordinate)
                               .thenComparingInt(Coordinate::getYCoordinate));
        for (Coordinate p : pellets) {
            sb.append(p.getXCoordinate()).append(',').append(p.getYCoordinate()).append(';');
        }
        return sb.toString();
    }
}

private Path<PelletVertex> buildPath(SearchNode goalNode) {
    List<SearchNode> chain = new ArrayList<>();
    for (SearchNode n = goalNode; n != null; n = n.parent) {
        chain.add(n);
    }

    Path<PelletVertex> result = null;
    for (int i = chain.size() - 1; i >= 0; i--) {
        SearchNode node = chain.get(i);
        if (result == null) {
            result = new Path<>(node.vertex);
        } else {
            result = new Path<>(node.vertex, node.gCost, result);
        }
    }
    return result;
}


    @Override
    public Set<Coordinate> getOutgoingNeighbors(final Coordinate source, final GameView game) {
    // Collect all valid neighbor coordinates that Pacman can move into.
    Set<Coordinate> neighbors = new HashSet<>();

    int x = source.getXCoordinate();
    int y = source.getYCoordinate();

    // Check each of the four directions for legality.
    if (game.isLegalPacmanMove(source, Action.NORTH)) {
        neighbors.add(new Coordinate(x, y - 1));
    }
    if (game.isLegalPacmanMove(source, Action.SOUTH)) {
        neighbors.add(new Coordinate(x, y + 1));
    }
    if (game.isLegalPacmanMove(source, Action.EAST)) {
        neighbors.add(new Coordinate(x + 1, y));
    }
    if (game.isLegalPacmanMove(source, Action.WEST)) {
        neighbors.add(new Coordinate(x - 1, y));
    }

    return neighbors;
}

    @Override
    public Path<Coordinate> graphSearch(final Coordinate start, final Coordinate goal, final GameView game) {
    // Dijkstra’s search (uniform edge cost = 1).
    PriorityQueue<NodeRecord> frontier = new PriorityQueue<>(Comparator.comparingDouble(n -> n.cost));
    Map<Coordinate, Coordinate> parentMap = new HashMap<>();
    Map<Coordinate, Float> bestCost = new HashMap<>();
    Set<Coordinate> closed = new HashSet<>();

    frontier.add(new NodeRecord(start, 0f));
    bestCost.put(start, 0f);
    parentMap.put(start, null);

    while (!frontier.isEmpty()) {
        NodeRecord current = frontier.poll();
        Coordinate here = current.position;

        if (closed.contains(here)) continue;
        closed.add(here);

        if (here.equals(goal)) break; // reached target

        for (Coordinate next : getOutgoingNeighbors(here, game)) {
            if (closed.contains(next)) continue;

            float stepCost = 1f + ghostRisk(next); // risk-aware edge cost
            float newCost = bestCost.get(here) + stepCost;

            if (!bestCost.containsKey(next) || newCost < bestCost.get(next)) {
                bestCost.put(next, newCost);
                parentMap.put(next, here);
                frontier.add(new NodeRecord(next, newCost));
            }
        }
    }

    // Reconstruct path from goal back to start.
    if (!parentMap.containsKey(goal)) return null;

    List<Coordinate> chain = new ArrayList<>();
    for (Coordinate step = goal; step != null; step = parentMap.get(step)) {
        chain.add(step);
    }

    // Convert reversed chain into Path<Coordinate>
    Path<Coordinate> path = null;
    for (int i = chain.size() - 1; i >= 0; i--) {
        Coordinate c = chain.get(i);
        path = (path == null) ? new Path<>(c) : new Path<>(c, 1f, path);
    }
    return path;
    }

    // Helper inner class to store frontier entries
    private static class NodeRecord {
    final Coordinate position;
    final float cost;
    NodeRecord(Coordinate position, float cost) {
        this.position = position;
        this.cost = cost;
    }
}


    @Override
    public void makePlan(final GameView game) {
    // Clear any old plan
    setPlanToGetToTarget(new Stack<>());

    Coordinate start  = game.getEntity(getPacmanId()).getCurrentCoordinate();
    Coordinate target = getTargetCoordinate();

    Path<Coordinate> foundPath = graphSearch(start, target, game);
    if (foundPath == null) {
        // no route
        return;
    }

    // Collect destinations from the Path chain
    List<Coordinate> seq = new ArrayList<>();
    for (Path<Coordinate> p = foundPath; p != null && p.getDestination() != null; p = p.getParentPath()) {
        seq.add(p.getDestination());
    }

    // seq is currently [goal, ..., start] with your Path builder -> make it [start, ..., goal]
    if (!seq.isEmpty()) {
        java.util.Collections.reverse(seq);
    }

    // Build the stack so that the TOP is the first step after start.
    // Skip seq[0] (start), include seq[1..end] (including goal).
    Stack<Coordinate> planStack = new Stack<>();
    for (int i = seq.size() - 1; i >= 1; i--) {
        planStack.push(seq.get(i));
    }

    setPlanToGetToTarget(planStack);
}



@Override
public Action makeMove(final GameView game) {
    try {
        updateGhostDistances(game);
        Coordinate current = game.getEntity(getPacmanId()).getCurrentCoordinate();
        Stack<Coordinate> plan = getPlanToGetToTarget();

        // If we already have a plan, follow it
        if (plan != null && !plan.isEmpty()) {
            Coordinate next = plan.pop();
            if (isValidMove(current, next, game)) {
                return Action.inferFromCoordinates(current, next);
            } else {
                return handlePlanFailure(game);
            }
        }

        // No plan: continue along the pellet-level path
        return continuePelletPath(game);
    } catch (Exception e) {
        // fallback random move
        return Action.values()[getRandom().nextInt(Action.values().length)];
    }
}

// Handles a broken or empty plan by resetting current targets
private Action handlePlanFailure(GameView game) {
    setPlanToGetToTarget(null);
    setTargetCoordinate(null);
    currentTargetVertex = null;
    return continuePelletPath(game);
}

// Continues following the global pellet path or creates a new one
private Action continuePelletPath(GameView game) {
    // Initialize A* search if no pellet path exists
    if (currentPelletPath == null) {
        currentPelletPath = findPathToEatAllPelletsTheFastest(game);
        if (currentPelletPath == null) {
            return Action.values()[getRandom().nextInt(Action.values().length)];
        }

        List<PelletVertex> vertices = convertPelletPathToList(currentPelletPath);
        pelletPathIterator = vertices.iterator();
        if (pelletPathIterator.hasNext()) pelletPathIterator.next(); // skip start
    }

    if (pelletPathIterator != null && pelletPathIterator.hasNext()) {
        currentTargetVertex = pelletPathIterator.next();
        Coordinate target = currentTargetVertex.getPacmanCoordinate();
        setTargetCoordinate(target);
        makePlan(game);
        return makeMove(game);
    } else {
        // Finished all pellets
        resetPelletPathState();
        return Action.values()[getRandom().nextInt(Action.values().length)];
    }
}

// Converts a Path<PelletVertex> into an ordered list
private List<PelletVertex> convertPelletPathToList(Path<PelletVertex> path) {
    List<PelletVertex> result = new ArrayList<>();
    for (Path<PelletVertex> p = path; p != null && p.getDestination() != null; p = p.getParentPath()) {
        result.add(p.getDestination());
    }
    return result;
}

// Resets pellet-related state
private void resetPelletPathState() {
    currentPelletPath = null;
    pelletPathIterator = null;
    currentTargetVertex = null;
    setPlanToGetToTarget(null);
    setTargetCoordinate(null);
}

// Validates that a planned step is still a legal move
private boolean isValidMove(Coordinate from, Coordinate to, GameView game) {
    if (from == null || to == null || !game.isInBounds(to)) return false;
    try {
        Action move = Action.inferFromCoordinates(from, to);
        return game.isLegalPacmanMove(from, move);
    } catch (Exception e) {
        return false;
    }
}

/** Rebuilds a BFS distance field from all ghosts so nearby tiles look dangerous. */
private void updateGhostDistances(GameView game) {
    ghostDistances.clear();

    // Collect all ghost coordinates using reflection (GameView doesn’t expose directly)
    List<Coordinate> ghosts = new ArrayList<>();
    try {
        java.lang.reflect.Method getGhostIdsMethod = game.getClass().getMethod("getGhostIds");
        Iterable<?> ghostIds = (Iterable<?>) getGhostIdsMethod.invoke(game);

        for (Object id : ghostIds) {
            java.lang.reflect.Method getEntityMethod = game.getClass().getMethod("getEntity", int.class);
            Object entity = getEntityMethod.invoke(game, (int) id);

            java.lang.reflect.Method getCoordMethod = entity.getClass().getMethod("getCurrentCoordinate");
            Object coord = getCoordMethod.invoke(entity);

            if (coord instanceof Coordinate) {
                ghosts.add((Coordinate) coord);
            }
        }
    } catch (Exception ignored) { }

    // Multi-source BFS expansion around ghosts
    ArrayDeque<Coordinate> q = new ArrayDeque<>();
    for (Coordinate g : ghosts) {
        ghostDistances.put(g, 0);
        q.add(g);
    }

    while (!q.isEmpty()) {
        Coordinate c = q.poll();
        int base = ghostDistances.get(c);
        if (base >= GHOST_RADIUS) continue;
        for (Coordinate nb : getOutgoingNeighbors(c, game)) {
            if (!ghostDistances.containsKey(nb)) {
                ghostDistances.put(nb, base + 1);
                q.add(nb);
            }
        }
    }
}

/** Returns an additional cost penalty for being close to a ghost. */
private float ghostRisk(Coordinate c) {
    Integer d = ghostDistances.get(c);
    if (d == null || d > GHOST_RADIUS) return 0f;
    float t = (GHOST_RADIUS - d) / (float) GHOST_RADIUS; // in (0,1]
    return GHOST_RISK_WEIGHT * t * t; // quadratic falloff
}




    @Override
    public void afterGameEnds(final GameView game)
    {

    }
}