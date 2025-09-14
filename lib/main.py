# SYSTEM IMPORTS
from typing import List, Type, Dict
import argparse as ap
import matplotlib.pyplot as plt
import matplotlib.collections as pltcollections
import matplotlib.ticker as pltticker
import numpy as np
import os
import sys
import time


# data structures needed for dijkstra/A*
import heapq as heap
from collections import defaultdict


# make sure the directory of this file is on path so we can load other files it depends on
_cd_: str = os.path.abspath(os.path.dirname(__file__))
if _cd_ in sys.path:
    sys.path.append(_cd_)
del _cd_


np.random.seed(12345)


# PYTHON PROJECT IMPORTS
from world import Action, Cell, CellColor, Maze


# quick and dirty maze solver agents

# A wrapper for the cell in the world (i.e. a state)
# From this class we know the cost of getting to this cell (from the start cell)
# and we know the parent of this node (i.e. a reversed linked-list back to the start cell)
PathNodeType = Type["PathNode"]
class PathNode(object):
    def __init__(self: PathNodeType,
                 cell: Cell,
                 path_cost: float = 0.0,
                 parent: PathNodeType = None
                 ) -> None:
        self.cell: Cell = cell
        self.path_cost: float = path_cost
        self.parent: PathNodeType = parent

    # in python if you want to use the heapq package, the cannot specify a custom comparator.
    # So, if we override the __lt__ method in this class, we can use it directly as a "value" in the heap
    def __lt__(self: PathNodeType,
               other: PathNodeType
               ) -> bool:
        return self.path_cost < other.path_cost

    def __eq__(self: PathNodeType,
               other: PathNodeType
               ) -> bool:
        return self.cell == other.cell

    def __hash__(self: PathNodeType) -> int:
        return self.cell.__hash__()

    def __str__(self: PathNodeType) -> str:
        return "({0}, {1})".format(self.cell, self.path_cost)

    def __repr__(self: PathNodeType) -> str:
        return str(self)

    @staticmethod
    def reverse_path(n: PathNodeType) -> List[Cell]:
        cells: List[Cell] = list()
        while n is not None:
            cells.append(n.cell)
            n = n.parent
        return cells[::-1]


def is_cell_in_list(c: Cell,
                    l: List[PathNode]
                    ) -> bool:
    for n in l:
        if n.cell == c:
            return True
    return False


def get_idx_of_cell_in_list(c: Cell,
                            l: List[PathNode]
                            ) -> int:
    for idx, n in enumerate(l):
        if n.cell == c:
            return idx
    return -1


BFSAgentType = Type["BFSAgent"]
class BFSAgent(object):
    def __init__(self: BFSAgentType,
                 maze: Maze,
                 action_costs: Dict[Action, float]
                 ) -> None:
        self.maze: Maze = maze
        self.action_costs: Dict[Action, float] = action_costs

        self.maze.reset()
        self.is_done: bool = False

        # normally these data structures would be self-contained within a "solve" method or something
        # like that, but I made them fields so that we can visualize the algorithm every iteration
        self.finalized_cells: Set[Cell] = set()
        self.node_queue: List[PathNode] = [PathNode(self.maze.current_cell,
                                                    path_cost=0.0,
                                                    parent=None)]
        self.best_path_from_current_node: Set[PathNode] = set()

    def reset(self: BFSAgentType) -> None:
        self.maze.reset()
        self.is_done = False

    def visualize(self: BFSAgentType,
                  rectangles: List[List[plt.Rectangle]]
                  ) -> None:
        for cell_row, rec_row in zip(self.maze.grid, rectangles):
            for cell, rec in zip(cell_row, rec_row):
                color_override = None
                if is_cell_in_list(cell, self.node_queue):
                    color_override = CellColor.GREEN
                elif cell in self.best_path_from_current_node:
                    color_override = CellColor.BLUE
                elif cell in self.finalized_cells:
                    color_override = CellColor.RED
                rec.set(color=CellColor.to_matplotlib_string(cell.get_color(color_override=color_override)))

    def solve_single_iter(self: BFSAgentType) -> None:
        if len(self.node_queue) == 0:
            self.is_done = True
        else:
            parent_node: PathNode = self.node_queue.pop(0)
            # only for visualization purposes
            self.best_path_from_current_node = set(PathNode.reverse_path(parent_node))
            # print("parent_node: ", parent_node)
            for action in self.maze.available_actions(parent_node.cell):
                # print("", "Action: ", action)
                if self.maze.is_valid_action(parent_node.cell, action):
                    child_cell: Cell = self.maze.apply_action(parent_node.cell, action)
                    if child_cell not in self.finalized_cells:

                        child_node: PathNode = PathNode(child_cell, path_cost=parent_node.path_cost + 1, parent=parent_node)

                        if child_cell == self.maze.goal_cell:
                            # found the goal! Set the path!
                            self.is_done = True
                            self.best_path_from_current_node = set(PathNode.reverse_path(child_node))

                        self.node_queue.append(child_node)

            self.finalized_cells.add(parent_node.cell)


DijkstraAgentType = Type["DijkstraAgent"]
class DijkstraAgent(object):
    def __init__(self: DijkstraAgentType,
                 maze: Maze,
                 action_costs: Dict[Action, float]
                 ) -> None:
        self.maze: Maze = maze
        self.action_costs: Dict[Action, float] = action_costs

        self.maze.reset()
        self.is_done: bool = False

        # normally these data structures would be self-contained within a "solve" method or something
        # like that, but I made them fields so that we can visualize the algorithm every iteration
        self.finalized_cells: Set[Cell] = set()
        self.node_heap: List[PathNode] = list()
        self.cell_costs: Dict[Cell, float] = defaultdict(lambda: float("inf"))
        self.cell_costs[self.maze.current_cell] = 0.0
        heap.heappush(self.node_heap, PathNode(self.maze.current_cell,
                                                path_cost=0.0,
                                                parent=None))
        self.best_path_from_current_node: Set[PathNode] = set()

    def reset(self: DijkstraAgentType) -> None:
        self.maze.reset()
        self.is_done = False
        self.cell_costs[self.maze.current_cell] = 0.0

    def visualize(self: DijkstraAgentType,
                  rectangles: List[List[plt.Rectangle]]
                  ) -> None:
        for cell_row, rec_row in zip(self.maze.grid, rectangles):
            for cell, rec in zip(cell_row, rec_row):
                color_override = None
                if is_cell_in_list(cell, self.node_heap):
                    color_override = CellColor.GREEN
                elif cell in self.best_path_from_current_node:
                    color_override = CellColor.BLUE
                elif cell in self.finalized_cells:
                    color_override = CellColor.RED
                rec.set(color=CellColor.to_matplotlib_string(cell.get_color(color_override=color_override)))

    def solve_single_iter(self: DijkstraAgentType) -> None:
        if len(self.node_heap) == 0:
            self.is_done = True
        else:
            parent_node = heap.heappop(self.node_heap)
            self.finalized_cells.add(parent_node.cell)

            if parent_node.cell == self.maze.goal_cell:
                # found the goal! Set the path!
                self.is_done = True
                self.best_path_from_current_node = set(PathNode.reverse_path(parent_node))


            # only for visualization purposes
            self.best_path_from_current_node = set(PathNode.reverse_path(parent_node))

            # print("parent_node: ", parent_node)
            for action in self.maze.available_actions(parent_node.cell):

                # print("", "Action: ", action)
                if self.maze.is_valid_action(parent_node.cell, action):
                    child_cell: Cell = self.maze.apply_action(parent_node.cell, action)
                    if child_cell not in self.finalized_cells:

                        action_cost: float = self.action_costs[action]
                        new_path_cost: float = parent_node.path_cost + action_cost
                        # print(new_path_cost, self.cell_costs[child_cell])
                        if self.cell_costs[child_cell] > new_path_cost:
                            self.cell_costs[child_cell] = new_path_cost

                            # delete old node
                            if is_cell_in_list(child_cell, self.node_heap):
                                old_node_idx: int = get_idx_of_cell_in_list(child_cell, self.node_heap)
                                self.node_heap[old_node_idx] = self.node_heap[-1]
                                self.node_heap.pop()
                                heap.heapify(self.node_heap)

                            child_node: PathNode = PathNode(child_cell,
                                                            path_cost=parent_node.path_cost + action_cost,
                                                            parent=parent_node)
                            heap.heappush(self.node_heap, child_node)

            # self.finalized_cells.add(parent_node.cell)




AStarAgentType = Type["AStarAgent"]
class AStarAgent(object):
    def __init__(self: AStarAgentType,
                 maze: Maze,
                 action_costs: Dict[Action, float],
                 p: int = 2
                 ) -> None:
        self.maze: Maze = maze
        self.action_costs: Dict[Action, float] = action_costs
        self.p: int = p

        self.maze.reset()
        self.is_done: bool = False

        # normally these data structures would be self-contained within a "solve" method or something
        # like that, but I made them fields so that we can visualize the algorithm every iteration
        self.finalized_cells: Set[Cell] = set()
        self.node_heap: List[PathNode] = list()
        self.cell_costs: Dict[Cell, float] = defaultdict(lambda: float("inf"))
        self.cell_costs[self.maze.current_cell] = 0.0
        heap.heappush(self.node_heap, PathNode(self.maze.current_cell,
                                                path_cost=0.0,
                                                parent=None))
        self.best_path_from_current_node: Set[PathNode] = set()

    def lp_distance(self: AStarAgentType,
                    cell: Cell,
                    goal_cell: Cell,
                    p: int = 2,
                    ) -> float:
        return (np.abs((cell.x - goal_cell.x) ** p) + np.abs((cell.y - goal_cell.y) ** p))**(1/p)

    def reset(self: AStarAgentType) -> None:
        self.maze.reset()
        self.is_done = False
        self.cell_costs[self.maze.current_cell] = 0.0

    def visualize(self: AStarAgentType,
                  rectangles: List[List[plt.Rectangle]]
                  ) -> None:
        for cell_row, rec_row in zip(self.maze.grid, rectangles):
            for cell, rec in zip(cell_row, rec_row):
                color_override = None
                if is_cell_in_list(cell, self.node_heap):
                    color_override = CellColor.GREEN
                elif cell in self.best_path_from_current_node:
                    color_override = CellColor.BLUE
                elif cell in self.finalized_cells:
                    color_override = CellColor.RED
                rec.set(color=CellColor.to_matplotlib_string(cell.get_color(color_override=color_override)))

    def solve_single_iter(self: AStarAgentType) -> None:
        if len(self.node_heap) == 0:
            self.is_done = True
        else:
            parent_node = heap.heappop(self.node_heap)
            self.finalized_cells.add(parent_node.cell)

            if parent_node.cell == self.maze.goal_cell:
                # found the goal! Set the path!
                self.is_done = True
                self.best_path_from_current_node = set(PathNode.reverse_path(parent_node))


            # only for visualization purposes
            self.best_path_from_current_node = set(PathNode.reverse_path(parent_node))

            # print("parent_node: ", parent_node)
            for action in self.maze.available_actions(parent_node.cell):

                # print("", "Action: ", action)
                if self.maze.is_valid_action(parent_node.cell, action):
                    child_cell: Cell = self.maze.apply_action(parent_node.cell, action)
                    if child_cell not in self.finalized_cells:

                        action_cost: float = self.action_costs[action]
                        cost_to_goal: float = self.lp_distance(child_cell, self.maze.goal_cell, p=self.p)
                        new_path_cost: float = parent_node.path_cost + action_cost + cost_to_goal
                        # print(new_path_cost, self.cell_costs[child_cell])
                        if self.cell_costs[child_cell] > new_path_cost:
                            self.cell_costs[child_cell] = new_path_cost

                            # delete old node
                            if is_cell_in_list(child_cell, self.node_heap):
                                old_node_idx: int = get_idx_of_cell_in_list(child_cell, self.node_heap)
                                self.node_heap[old_node_idx] = self.node_heap[-1]
                                self.node_heap.pop()
                                heap.heapify(self.node_heap)

                            child_node: PathNode = PathNode(child_cell,
                                                            path_cost=new_path_cost,
                                                            parent=parent_node)
                            heap.heappush(self.node_heap, child_node)

            # self.finalized_cells.add(parent_node.cell)


DFSAgentType = Type["DFSAgent"]
class DFSAgent(object):
    def __init__(self: DFSAgentType,
                 maze: Maze,
                 action_costs: Dict[Action, float]
                 ) -> None:
        self.maze: Maze = maze
        self.action_costs: Dict[Action, float] = action_costs
        self.maze.reset()

        self.is_done: bool = False

        # normally these data structures would be self-contained within a "solve" method or something
        # like that, but I made them fields so that we can visualize the algorithm every iteration
        self.node_stack: List[PathNode] = [PathNode(self.maze.current_cell,
                                                path_cost=0.0,
                                                parent=None)]
        self.best_path_from_current_node: Set[PathNode] = set()
        self.finalized_cells: Set[Cell] = set()

    def reset(self: AStarAgentType) -> None:
        self.maze.reset()
        self.is_done = False

    def visualize(self: DFSAgentType,
                  rectangles: List[List[plt.Rectangle]]
                  ) -> None:
        for cell_row, rec_row in zip(self.maze.grid, rectangles):
            for cell, rec in zip(cell_row, rec_row):
                color_override = None
                if is_cell_in_list(cell, self.node_stack):
                    color_override = CellColor.GREEN
                elif cell in self.best_path_from_current_node:
                    color_override = CellColor.BLUE
                elif cell in self.finalized_cells:
                    color_override = CellColor.RED
                rec.set(color=CellColor.to_matplotlib_string(cell.get_color(color_override=color_override)))

    def solve_single_iter(self: DFSAgentType) -> None:
        if len(self.node_stack) == 0:
            self.is_done = True
        else:
            parent_node: PathNode = self.node_stack.pop()
            self.finalized_cells.add(parent_node.cell)

            # only for visualization purposes
            self.best_path_from_current_node = set(PathNode.reverse_path(parent_node))

            for action in self.maze.available_actions(parent_node.cell):

                # print("", "Action: ", action)
                if self.maze.is_valid_action(parent_node.cell, action):
                    child_cell: Cell = self.maze.apply_action(parent_node.cell, action)
                    child_node: PathNode = PathNode(child_cell,
                                                    path_cost=parent_node.path_cost + 1,
                                                    parent=parent_node)

                    if child_cell == self.maze.goal_cell:
                        # found it!
                        self.is_done = True
                        self.best_path_from_current_node = set(PathNode.reverse_path(child_node))

                    if child_cell not in self.finalized_cells:
                        self.node_stack.append(child_node)


def main() -> None:
    parser = ap.ArgumentParser()
    parser.add_argument("num_x_coords", type=int, help="number of x coords in maze")
    parser.add_argument("num_y_coords", type=int, help="number of y coords in maze")

    parser.add_argument("agent", type=str, default="bfs", choices=["bfs", "astar", "dijkstra", "dfs"])

    parser.add_argument("--start_x", type=int, default=0, help="x coord of start state")
    parser.add_argument("--start_y", type=int, default=0, help="y coord of start state")
    parser.add_argument("--p", type=int, default=2, help="p norm value")

    parser.add_argument("--lc", type=float, default=1.0, help="cost of going LEFT")
    parser.add_argument("--rc", type=float, default=1.0, help="cost of going RIGHT")
    parser.add_argument("--uc", type=float, default=1.0, help="cost of going UP")
    parser.add_argument("--dc", type=float, default=1.0, help="cost of going DOWN")

    args = parser.parse_args()

    CELL_WIDTH: int = 1
    action_costs: Dict[Action, float] = {
        Action.LEFT: args.lc,
        Action.RIGHT: args.rc,
        Action.UP: args.uc,
        Action.DOWN: args.dc
    }

    maze: Maze = Maze(args.num_x_coords,
                      args.num_y_coords,
                      init_x=args.start_x,
                      init_y=args.start_y)
    agent: object = None

    if args.agent == "bfs":
        agent = BFSAgent(maze, action_costs)
    elif args.agent == "astar":
        agent = AStarAgent(maze, action_costs, p=args.p)
    elif args.agent == "dijkstra":
        agent = DijkstraAgent(maze, action_costs)
    elif args.agent == "dfs":
        agent = DFSAgent(maze, action_costs)
    else:
        raise Exception("ERROR: unknown agent type [{0}]".format(args.agent))

    plt.ion()
    figure, ax = plt.subplots()
    ax.set_xlim(left=0, right=maze.num_x_coords)
    ax.set_ylim(bottom=0, top=maze.num_y_coords)

    rectangles: List[object] = [[plt.Rectangle((cell.x, cell.y),
                                               CELL_WIDTH, CELL_WIDTH,
                                               color=CellColor.to_matplotlib_string(cell.get_color()))
                                 for cell in row]
                                for row in maze.grid]

    for row in rectangles:
        for rec in row:
            ax.add_artist(rec)
    # ax.add_collection(pltcollections.PatchCollection(rectangles))

    # add walls
    for row in maze.grid:
        for cell in row:
            bx, by = cell.x, cell.y
            tx, ty = cell.x + CELL_WIDTH, cell.y + CELL_WIDTH

            if Action.LEFT in cell.walls:
                ax.plot([bx, bx], [by, ty], color=CellColor.to_matplotlib_string(CellColor.BLACK))
            if Action.RIGHT in cell.walls:
                ax.plot([tx, tx], [by, ty], color=CellColor.to_matplotlib_string(CellColor.BLACK))
            if Action.DOWN in cell.walls:
                ax.plot([bx, tx], [by, by], color=CellColor.to_matplotlib_string(CellColor.BLACK))
            if Action.UP in cell.walls:
                ax.plot([bx, tx], [ty, ty], color=CellColor.to_matplotlib_string(CellColor.BLACK))

    agent.visualize(rectangles)
    figure.canvas.draw()
    figure.canvas.flush_events()
    # time.sleep(0.1)

    agent.visualize(rectangles)
    figure.canvas.draw()
    figure.canvas.flush_events()
    time.sleep(10)
    # return

    while not agent.is_done:
        figure.canvas.draw()
        figure.canvas.flush_events()

        agent.solve_single_iter()
        agent.visualize(rectangles)
        # rectangles[0][5].set(color=CellColor.to_matplotlib_string(list(CellColor)[idx % len(list(CellColor))]))

        # time.sleep(0.1)

    agent.visualize(rectangles)
    figure.canvas.draw()
    figure.canvas.flush_events()
    time.sleep(5)


if __name__ == "__main__":
    main()

