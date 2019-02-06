package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
        extends SequentialSolver {
    protected class ForkState {
        final ForkJoinSolver thread;
        final int start;

         ForkState(ForkJoinSolver thread, int start) {
            this.thread = thread;
            this.start = start;
        }
    }

    /**
     * Set of identifiers of all nodes visited so far during the
     * search.
     */
    static private ConcurrentSkipListSet<Integer> visited = new ConcurrentSkipListSet<>();

    /**
     * Lets threads know that they can quit their search
     */
    static private AtomicBoolean pathFound = new AtomicBoolean();

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze the maze to be searched
     */
    public ForkJoinSolver(Maze maze) {
        super(maze);
        visited.add(start);
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * new start node to a goal.
     * @param start
     * @param maze
     */
    private ForkJoinSolver(int start, Maze maze) {
        super(maze);
        this.start = start;
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after
     *                  which a parallel task is forked; if
     *                  <code>forkAfter &lt;= 0</code> the solver never
     *                  forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a
     * goal node in the maze; <code>null</code> if such a path cannot
     * be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        //visited.add(start);
        return parallelSearch(start);
    }

    private List<Integer> parallelSearch(int start) {
        //Needed to store the result untill all forked threads have joined
        List<Integer> pathFromTo = null;

        int player = maze.newPlayer(start);
        // start with start node
        frontier.push(start);

        List<ForkState> threads = new ArrayList<>();

        // as long as not all nodes have been processed
        while (!frontier.empty()) {
            // get the new node to process
            int current = frontier.pop();

            // if current node has a goal
            if (maze.hasGoal(current)) {
                // move player to goal
                maze.move(player, current);
                // search finished: reconstruct path
                pathFound.set(true);
                pathFromTo = pathFromTo(start, current);
                break;
            }
            //If goal is already found by another solver
            if (pathFound.get()) {
                break;
            }
            // move player to current node
            maze.move(player, current);
            //Number of new, not visited, neighbors
            int newNeighbors = 0;

            for (int nb : maze.neighbors(current)) {
                if (visited.add(nb)) {
                    newNeighbors++;
                } else {
                    //Node already in visited set
                    continue;
                }
                //Fork only if there's more than one new tile to explore
                if (newNeighbors == 1) {
                    frontier.push(nb);
                    predecessor.put(nb, current);
                } else if (newNeighbors > 1) {
                    ForkJoinSolver fs = new ForkJoinSolver(nb, maze);
                    //Add a tuple with the solver and its start tile
                    threads.add(new ForkState(fs, current));
                    fs.fork();
                }
            }
        }
        //Join all forked threads
        for (ForkState thread : threads) {
            List<Integer> path = thread.thread.join();
            if (thread.thread.join() != null) {
                pathFromTo = pathFromTo(start, thread.start);
                pathFromTo.addAll(path);
            }
        }
        //Will be null if no path was found
        return pathFromTo;
    }
}
