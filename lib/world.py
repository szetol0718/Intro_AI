# SYSTEM IMPORTS
from enum import Enum, unique
from typing import Dict, Set, Type, Tuple, Union, List
import numpy as np


# PYTHON PROJECT IMPORTS


ActionType = Type["Action"]

@unique
class Action(Enum):
    UP = 1
    DOWN = 2
    LEFT = 3
    RIGHT = 4

    @staticmethod
    def invert(a: ActionType) -> ActionType:
        a_inverse: Action = None

        if a == Action.UP:
            a_inverse = Action.DOWN
        elif a == Action.DOWN:
            a_inverse = Action.UP
        elif a == Action.LEFT:
            a_inverse = Action.RIGHT
        elif a == Action.RIGHT:
            a_inverse = Action.LEFT
        else:
            raise Exception("ERROR: unknown action [{0}]".format(a))

        return a_inverse


# this class defines the colors that a cell should be filled in with in the visualization.
# if the cell is occupied: its color will be BLACK
# if the cell is the not occupied, there are a few rules which determine the color:
#     if the cell is a start state: the color will be yellow        
#     else if the cell is on the shortest path currently being viewed, its color will be BLUE
#     else if the cell is finalized by the algorithm, its color will be RED
#     else if the cell is on the boundary its color will be GREEN
#     else if the cell is the goal state its color will be PURPLE
#     else its color will be WHITE
CellColorType = Type["CellColor"]
@unique
class CellColor(Enum):
    BLACK = 0
    YELLOW = 1
    BLUE = 2
    GREEN = 3
    WHITE = 4
    PURPLE = 5
    RED = 6

    @staticmethod
    def to_matplotlib_string(c: CellColorType) -> str:
        color: str = None

        if c == CellColor.BLACK:
            color = "black"
        elif c == CellColor.YELLOW:
            color = "yellow"
        elif c == CellColor.BLUE:
            color = "blue"
        elif c == CellColor.GREEN:
            color = "green"
        elif c == CellColor.WHITE:
            color = "white"
        elif c == CellColor.PURPLE:
            color = "purple"
        elif c == CellColor.RED:
            color = "red"
        else:
            raise Exception("ERROR: unknown cell color [{0}]".format(c))

        return color


# code for the Cell and Maze classes was adapted from https://scipython.com/blog/making-a-maze/
CellType = Type["Cell"]
class Cell(object):
    def __init__(self: CellType,
                 x: int,
                 y: int
                 ) -> None:
        self.x: int = x
        self.y: int = y

        # if the square is an obstacle (i.e. not available) then this will be False
        self.available_actions: Set[Action] = set()
        self.walls: Set[Action] = {Action.LEFT, Action.RIGHT, Action.UP, Action.DOWN}
        self.is_available: bool = False
        self.is_start_state: bool = False
        self.is_goal_state: bool = False

    def knock_down_wall(self: CellType,
                        other: CellType,
                        action: Action
                        ) -> None:
        self.walls.remove(action)
        self.available_actions.add(action)

        other.walls.remove(Action.invert(action))
        other.available_actions.add(Action.invert(action))

    def __hash__(self: CellType) -> int:
        return (self.x, self.y).__hash__()

    def __eq__(self: CellType,
               other: CellType) -> bool:
        return (self.x, self.y) == (other.x, other.y)

    def __str__(self: CellType) -> str:
        return "({0}, {1})".format(self.x, self.y)

    def __repr__(self: CellType) -> str:
        return str(self)

    def get_color(self: CellType,
                  color_override: CellColor = None
                  ) -> CellColor:
        # this method will determine the color that this cell should be filled in with in the visualization.
        # if the cell is occupied: its color will be BLACK
        # if the cell is the not occupied, there are a few rules which determine the color:
        #     if the cell is a start state: the color will be yellow        
        #     else if the cell is on the shortest path currently being viewed, its color will be BLUE
        #     else if the cell is finalized by the algorithm, its color will be RED
        #     else if the cell is on the boundary of the algorithm, its color will be GREEN
        #     else the color will be WHITE

        # most of these color options will be provided by the color_override argument
        color: CellColor = None
        if not self.is_available:
            color = CellColor.BLACK
        elif self.is_start_state:
            color = CellColor.YELLOW
        elif self.is_goal_state:
            color = CellColor.PURPLE
        else:
            if color_override is not None:
                color = color_override
            else:
                color = CellColor.WHITE
        return color


MazeType = Type["Maze"]
class Maze(object):
    def __init__(self: MazeType,
                 num_x_coords: int,
                 num_y_coords: int,
                 init_x: int = 0,
                 init_y: int = 0,
                 goal_x: int = None,
                 goal_y: int = None
                 ) -> None:
        self.num_x_coords: int = num_x_coords
        self.num_y_coords: int = num_y_coords

        # start state
        self.init_x: int = init_x
        self.init_y: int = init_y

        # goal state
        self.goal_x: int = goal_x
        self.goal_y: int = goal_y

        if self.goal_x is None:
            self.goal_x = self.num_x_coords - 1
        if self.goal_y is None:
            self.goal_y = self.num_y_coords - 1

        # the grid
        self.grid: List[List[Cell]] = [[Cell(x, y) for y in range(self.num_y_coords)]
                                        for x in range(self.num_x_coords)]

        # current agent state
        self.current_cell: Cell = self.get_cell(self.init_x, self.init_y)
        self.current_cell.is_start_state = True

        # set the goal state
        self.goal_cell = self.get_cell(self.goal_x, self.goal_y)
        self.goal_cell.is_goal_state = True

        # make the walls in the maze
        self.make_paths_prim()

    def reset(self: MazeType) -> None:
        self.current_cell = self.get_cell(self.init_x, self.init_y)

    def is_on_edge(self: MazeType,
                   cell: Cell
                   ) -> bool:
        return cell.x == 0 or cell.x == self.num_x_coords-1 or cell.y == 0 or cell.y == self.num_x_coords-1

    def is_valid_action(self: MazeType,
                        cell: Cell,
                        action: Action,
                        check_availability: bool = True
                        ) -> bool:
        valid: bool = True
        if action == Action.DOWN and cell.y == 0:
            valid = False
        elif action == Action.UP and cell.y == self.num_y_coords-1:
            valid = False
        elif action == Action.LEFT and cell.x == 0:
            valid = False
        elif action == Action.RIGHT and cell.x == self.num_x_coords-1:
            valid = False
        elif check_availability:
            valid = action in cell.available_actions
        return valid

    def make_paths_prim(self: MazeType) -> None:
        self.reset()
        self.current_cell.is_available = True

        # this uses randomized prim's algorithm. If you are unfamiliar with it, I suggest reading this
        # medium article on it with comes with a python implementation:
        # https://medium.com/swlh/fun-with-python-1-maze-generator-931639b4fb7e

        # randomly pick a spot in the maze to begin with...we cannot start on an edge, but that is ok
        # because we can use our starting cell (which is not allowed to be on an edge)
        start_cell: Cell = self.current_cell

        walls: List[Tuple[Action, Cell]] = [(a, start_cell) for a in start_cell.walls]
        while len(walls) > 0:
            rand_idx: int = np.random.randint(len(walls))
            rand_action, parent_cell = walls.pop(rand_idx)
            # print(parent_cell)

            if self.is_valid_action(parent_cell, rand_action, check_availability=False):
                next_cell: Cell = self.apply_action(parent_cell, rand_action, check=False)
                if (parent_cell.is_available and not next_cell.is_available) or\
                   (not parent_cell.is_available and next_cell.is_available):
                    # break down that wall....basically mark the next_cell as available
                    parent_cell.knock_down_wall(next_cell, rand_action)
                    next_cell.is_available = True
                    walls.extend([(a, next_cell) for a in next_cell.walls])

    def get_cell(self: MazeType,
                 x: int,
                 y: int
                 ) -> Cell:
        return self.grid[x][y]

    def available_actions(self: MazeType,
                          cell: Cell,
                          ) -> Set[Action]:
        x: int = cell.x
        y: int = cell.y

        actions: Set[Action] = cell.available_actions
        if 0 == x:
            actions -= {Action.LEFT}
        if x == self.num_x_coords - 1:
            actions -= {Action.RIGHT}

        if 0 == y:
            actions -= {Action.DOWN}
        if y == self.num_y_coords - 1:
            actions -= {Action.UP}

        return actions - cell.walls

    def apply_action(self: MazeType,
                     cell: Cell,
                     action: Action,
                     check: bool = True
                     ) -> Cell:
        if check and (action not in self.available_actions(cell)):
            raise Exception("ERROR: action [{0}] is not available for cell {1}".format(action, cell))

        next_cell: Cell = None
        if action == Action.UP:
            next_cell = self.get_cell(cell.x, cell.y+1)
        elif action == Action.DOWN:
            next_cell = self.get_cell(cell.x, cell.y-1)
        elif action == Action.LEFT:
            next_cell = self.get_cell(cell.x-1, cell.y)
        elif action == Action.RIGHT:
            next_cell = self.get_cell(cell.x+1, cell.y)
        else:
            raise Exception("ERROR: unknown action [{0}]".format(action))
        return next_cell

    def get_neighbors(self: MazeType,
                      cell: Cell,
                      get_actions: bool = False
                      ) -> Union[Set[Cell], Set[Tuple[Cell, Action]]]:
        ordered_actions: List[Action] = list(self.available_actions(cell))
        ordered_neighbors: List[Cell] = list()

        for a in ordered_actions:
            ordered_neighbors.append(self.apply_action(cell, a))

        if get_actions:
            return set(zip(ordered_neighbors, ordered_actions))
        else:
            return set(ordered_neighbors)
