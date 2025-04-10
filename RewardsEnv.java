import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.FileWriter; // for logging
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class RewardsEnv extends Environment {

    public static final int GSize = 9; // Grid size

    static Logger logger = Logger.getLogger(RewardsEnv.class.getName());

    private RewardsModel model;
    private RewardsView view;

    @Override
    public void init(String[] args) {
        model = new RewardsModel(GSize);
        view = new RewardsView(model);
        model.setView(view);
        updatePercepts();
    }

    @Override
    public boolean executeAction(String ag, Structure action) {
        logger.info(ag + " doing: " + action);
        int agentId;

        try {
            switch (action.getFunctor()) {
                case "resolve_collision":
                    model.resolveCollision();
                    break;

                case "move_towards":
                    agentId = (int) ((NumberTerm) action.getTerm(0)).solve();
                    model.moveTowards(agentId);
                    break;

                case "algorithm":
                    agentId = (int) ((NumberTerm) action.getTerm(0)).solve();
                    model.algorithm(agentId);
                    break;

                case "get_moves":
                    // Get the agent ID based on the agent name
                    agentId = ag.equals("explorer") ? 0 : 1;
                    int movesLeft = model.MAX_MOVES - model.getMoves(agentId);
                    
                    // Add the moves left perception
                    Literal moves_left = Literal.parseLiteral("moves(" + movesLeft + ")");
                    addPercept(ag, moves_left);
                    break;

                case "get_steps":
                    // Get the agent ID based on the agent name
                    agentId = ag.equals("explorer") ? 0 : 1;
                    int steps_left = model.getSteps(agentId);

                    Literal steps = Literal.parseLiteral("steps(" + steps_left + ")");
                    addPercept(ag, steps);
                    break;

                case "collect":
                    agentId = (int) ((NumberTerm) action.getTerm(0)).solve();
                    model.collectReward(agentId);
                    break;

                case "print_results":
                    model.printResults();
                    break;

                default:
                    return false;
            }
        } catch (Exception e) {
            logger.severe("Error executing action: " + action + ". Exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        updatePercepts();

        synchronized (this) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Preserve interrupt status
                logger.warning("Thread interrupted: " + e.getMessage());
            }
        }

        informAgsEnvironmentChanged();
        return true;
    }

    // Creates the agent's perceptions based on the RewardsModel
    void updatePercepts() {
        clearPercepts();  // Clear previous percepts

        logger.info("Updating percepts...");
        
        // For explorer
        Location agentLoc0 = model.getAgPos(0);
        Literal pos0 = Literal.parseLiteral("pos(explorer," + agentLoc0.x + "," + agentLoc0.y + ")");
        addPercept("explorer", pos0);
        
        // For explorer1
        Location agentLoc1 = model.getAgPos(1);
        Literal pos1 = Literal.parseLiteral("pos(explorer1," + agentLoc1.x + "," + agentLoc1.y + ")");
        addPercept("explorer1", pos1);
        
        addPercept("explorer", Literal.parseLiteral("done(false)"));
        addPercept("explorer1", Literal.parseLiteral("done(false)"));
        
        if (model.isCollision()) {
            Literal col = Literal.parseLiteral("isCollision(true)");
            // Notify all agents of the collision
            addPercept("explorer", col);
            addPercept("explorer1", col);
        } else {
            // Notify all agents there is no collision
            addPercept("explorer", Literal.parseLiteral("isCollision(false)"));
            addPercept("explorer1", Literal.parseLiteral("isCollision(false)"));
        }
    }

    class RewardsModel extends GridWorldModel {

        public static final int BLUE = 16;   // Color blocks
        public static final int GREEN = 32;
        public static final int YELLOW = 64;
        public static final int PURPLE = 128;
        public static final int OBSTACLE = 256; // OBSTACLE block
        
        private static final int MAX_MOVES = 31; // Maximum moves allowed per agent
        private static final double MOVE_COST = 0.01; // Penalty for each move
        private static final int NUM_AGENTS = 2; // Support 2 agents

        private Map<Integer, List<Location>> agentPaths = new HashMap<>();  // Keep the current paths of all agents
        private Map<Integer, Double> agentScores = new HashMap<>(); // Score of each agent
        private Map<Integer, Integer> agentMoves = new HashMap<>(); // Moves left for each agent
        private Map<Integer, Integer> agentPathSizes = new HashMap<>(); // Cached path sizes for each agent

        private List<String> actionLog = new ArrayList<>(); // To calculate the statistics

        // Seeding with system time to ensure a unique random sequence each run.
        Random random = new Random(System.currentTimeMillis());

        private RewardsModel(int GSize) {
            super(GSize, GSize, NUM_AGENTS);

            // Initialize agents' paths, scores, and moves
            for (int i = 0; i < NUM_AGENTS; i++) {
                agentPaths.put(i, new ArrayList<>());
                agentScores.put(i, 0.0);
                agentMoves.put(i, 0);
            }

            // Place the agents randomly
            try {
                setAgPos(0, getRandomEmptyLocation());
                setAgPos(1, getRandomEmptyLocation());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Place reward blocks
            add(BLUE, getRandomEmptyLocation());
            add(GREEN, getRandomEmptyLocation());
            add(YELLOW, getRandomEmptyLocation());
            add(PURPLE, getRandomEmptyLocation());

            // Add 4 obstacles
            for (int i = 0; i < 4; i++) {
                add(OBSTACLE, getRandomEmptyLocation());
            }
        }

        // Get a location on the grid that isn't already occupied
        private Location getRandomEmptyLocation() {
            Location loc;
            do {            
                loc = new Location(random.nextInt(getWidth()), random.nextInt(getHeight())); // gets a random (0-9, 0-9) loc, checks if its free.
            } while (!isFree(loc) || hasObject(BLUE, loc) || hasObject(GREEN, loc) || hasObject(YELLOW, loc) || hasObject(PURPLE, loc) || hasObject(OBSTACLE, loc));
            return loc;
        }
        
        public void setPath(int agentId, List<Location> path) {
            agentPaths.put(agentId, path);
            if (path != null) {
                agentPathSizes.put(agentId, path.size()); // Cache the path size
            }
        }

        // Get the path of a specific agent
        public List<Location> getPath(int agentId) {
            return agentPaths.get(agentId);
        }

        public int getMoves(int agentId) {
            return agentMoves.get(agentId);
        }

        public int getSteps(int agentId) {
            return agentPathSizes.getOrDefault(agentId, 0);
        }

        public double getScore(int agentId) {
            return agentScores.get(agentId);
        }

        public void setScore(int agentId, double score) {
            agentScores.put(agentId, score);
        }


        boolean isCollision() {
            List<Location> agPath0 = getPath(0);
            List<Location> agPath1 = getPath(1);
            
            if (agPath0.isEmpty() || agPath1.isEmpty()) {
                return false; // No collision is possible if either agent has no path
            }

            Location nextStep0 = agPath0.get(0);
            Location nextStep1 = agPath1.get(0);

            Location pos0 = getAgPos(0);
            Location pos1 = getAgPos(1);
            
            if ( (nextStep0.x == nextStep1.x && nextStep0.y == nextStep1.y ) || 
                    (nextStep0.x == pos1.x  && nextStep0.y == pos1.y) || 
                    (nextStep1.x == pos0.x && nextStep1.y == pos0.y) || 
                    (pos0.x == pos1.x && pos0.y == pos1.y) ) {
                System.err.println("CHEESEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");
                return true;
            }
            return false;
        }

        // Resolves collision between agents
        private void resolveCollision() {
            List<Location> path0 = getPath(0);
            List<Location> path1 = getPath(1);
            
            Location pos0 = getAgPos(0);
            Location pos1 = getAgPos(1);
            
            if (path0 == null || path1 == null || path0.isEmpty() || path1.isEmpty()) {
                return; // Nothing to resolve if paths are null or empty
            }

            Location nextStep0 = path0.get(0);
            Location nextStep1 = path1.get(0);
            
            Location lastStep0 = path0.get(path0.size() - 1);
            Location lastStep1 = path1.get(path1.size() - 1);

            if (lastStep0.x == lastStep1.x && lastStep0.y == lastStep1.y) {
                // Both agents are moving to the same reward
                double dist0 = heuristic(pos0, lastStep0);
                double dist1 = heuristic(pos1, lastStep1);

                if (dist0 < dist1) {
                    // Agent 0 is closer, reassign Agent 1
                    System.out.println("Collision resolved: Agent 0 proceeds, Agent 1 reassigns.");
                    reassignAgent(1);
                } else if (dist1 < dist0) {
                    // Agent 1 is closer, reassign Agent 0
                    System.out.println("Collision resolved: Agent 1 proceeds, Agent 0 reassigns.");
                    reassignAgent(0);
                } else {
                    // Equal distances; pick one randomly to proceed
                    int chosen = random.nextBoolean() ? 0 : 1;
                    System.out.println("Collision resolved randomly: Agent " + chosen + " proceeds.");
                    reassignAgent(chosen == 0 ? 1 : 0);
                }
            } else {
                // Paths are different, but the agents might collide by crossing each other
                if ( (nextStep0.x == nextStep1.x && nextStep0.y == nextStep1.y ) || 
                        (nextStep0.x == pos1.x  && nextStep0.y == pos1.y) || 
                        (nextStep1.x == pos0.x && nextStep1.y == pos0.y) || 
                        (pos0.x == pos1.x && pos0.y == pos1.y) ) {
                    
                    System.out.println("Agents crossing each other. Replanning paths.");
                    reassignAgent(0);
                    reassignAgent(1);
                }
            }

            Literal done = Literal.parseLiteral("done(true)");
            addPercept("explorer", done);
            addPercept("explorer1", done);

            Literal col = Literal.parseLiteral("isCollision(false)");
            addPercept("explorer", col);
            addPercept("explorer1", col);
        }

        // Reassigns the path of an agent to the next closest reward
        private void reassignAgent(int agentId) {
            System.out.println("Reassigning path for Agent " + agentId);
            
            int otherAgentId = 1 - agentId;
            List<Location> otherAgentPath = getPath(otherAgentId);
            Location otherAgentTarget = null;
            
            if (otherAgentPath != null && !otherAgentPath.isEmpty()) {
                otherAgentTarget = otherAgentPath.get(otherAgentPath.size() - 1); // Last location in the path is the target
            }

            // Define reward values
            Map<Integer, Double> rewardValues = new HashMap<>();
            rewardValues.put(BLUE, 0.6);
            rewardValues.put(GREEN, 0.8);
            rewardValues.put(YELLOW, 0.3);
            rewardValues.put(PURPLE, 0.2);

            // Locate all rewards
            List<Location> rewardLocations = new ArrayList<>();
            for (int x = 0; x < GSize; x++) {
                for (int y = 0; y < GSize; y++) {
                    Location loc = new Location(x, y);
                    if (hasObject(BLUE, loc) || hasObject(GREEN, loc) ||
                        hasObject(YELLOW, loc) || hasObject(PURPLE, loc)) {
                        rewardLocations.add(loc);
                    }
                }
            }

            // A* search to find closest and most valuable reward
            Location agentStart = getAgPos(agentId);
            Location bestReward = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            List<Location> bestPath = null;

            for (Location reward : rewardLocations) {
                if (reward.x == otherAgentTarget.x && reward.y == otherAgentTarget.y) {
                    continue; // Skip the reward if it's the current target of the other agent
                }
                List<Location> path = aStarPath(agentStart, reward, agentId);
                if (path == null) {
                    continue; // Skip unreachable rewards
                }

                double distance = path.size();
                double value = 0.0;

                // Find reward type at the location
                for (int rewardType : new int[]{BLUE, GREEN, YELLOW, PURPLE}) {
                    if (hasObject(rewardType, reward)) {
                        value = rewardValues.get(rewardType);
                        break;
                    }
                }

                // Calculate value-to-distance ratio
                double score = value / distance;
                int movesLeft = MAX_MOVES - agentMoves.get(agentId);
                if (score > bestScore && movesLeft >= distance) {
                    bestScore = score;
                    bestReward = reward;
                    bestPath = path;
                }
            }

            if (bestPath != null && !bestPath.isEmpty()) {
                System.err.println("Agent " + agentId + ": Best Path to Reward: " + bestPath);
                setPath(agentId, bestPath); // Assign the path to the agent
            } else {
                System.out.println("No valid path found for Agent " + agentId + ".");
            }
        }


        void moveTowards(int agentId) throws Exception {
            int moves = agentMoves.get(agentId); // Get the current moves for this agent
            if (moves >= MAX_MOVES) {
                System.out.println("Maximum moves reached. Exploration over!");
                System.out.printf("Final score for agent %d is: %.2f%n", agentId, agentScores.get(agentId));
                actionLog.add("Run complete. Final score for agent " + agentId + ": " + agentScores.get(agentId));
                saveLogToFile();
                return;
            }
            
            List<Location> currentPath = agentPaths.get(agentId);
            if (currentPath == null || currentPath.isEmpty()) {
                System.out.println("No path to follow for agent " + agentId);
                return;
            }
        
            Location currentPos = getAgPos(agentId);
            Location nextStep = currentPath.get(0); // Get next step without removing it yet

            // Check if the next step is valid and there's no collision
            if (!hasObject(OBSTACLE, nextStep) && !isCollision()) {
                agentPaths.get(agentId).remove(0);
                setAgPos(agentId, nextStep.x, nextStep.y);
                
                moves++;
                agentMoves.put(agentId, moves);

                double agentScore = agentScores.get(agentId) - MOVE_COST;
                setScore(agentId, agentScore); // Update the agent's score
                
                logger.info("Agent " + agentId + " moved from " + currentPos + " to " + nextStep);
            } else {
                System.out.println("Agent " + agentId + " blocked by obstacle or collision.");
                logger.info("COLLISION");
            }
        }        

        // A* Algorithm
        void algorithm(int agentId) {
            // Define reward values
            Map<Integer, Double> rewardValues = new HashMap<>(); // HashMap stores items in "key/value" pairs
            rewardValues.put(BLUE, 0.6);
            rewardValues.put(GREEN, 0.8);
            rewardValues.put(YELLOW, 0.3);
            rewardValues.put(PURPLE, 0.2);

            // Locate all rewards
            List<Location> rewardLocations = new ArrayList<>();
            // Return the position of all rewards on the environment
            for (int x = 0; x < GSize; x++) {
                for (int y = 0; y < GSize; y++) {
                    Location loc = new Location(x, y);
                    if (model.hasObject(BLUE, loc) || model.hasObject(GREEN, loc) ||
                        model.hasObject(YELLOW, loc) || model.hasObject(PURPLE, loc)) {
                        rewardLocations.add(loc);
                    }
                }
            }
            
            // A* search to find closest and most valuable reward
            Location agentStart = getAgPos(agentId);
            Location bestReward = null;
            double bestScore = Double.NEGATIVE_INFINITY;  // Set like that so that any score will always be more than its initial value
            List<Location> bestPath = null;
            
            for (Location reward : rewardLocations) {
                List<Location> path = aStarPath(agentStart, reward, agentId);
                if (path == null) continue; // Skip unreachable rewards

                double distance = path.size();
                double value = 0.0;
                
                // Find reward type at the location
                for (int rewardType : new int[]{BLUE, GREEN, YELLOW, PURPLE}) {
                    if (hasObject(rewardType, reward)) {
                        value = rewardValues.get(rewardType);
                        break;
                    }
                }
                
                // Calculate value-to-distance ratio
                double score = value / distance;
                int movesLeft = MAX_MOVES - agentMoves.get(agentId);  // This ensures the agent won't waste moves on moves he cannot reach.
                if (score > bestScore && movesLeft >= distance) {
                    bestScore = score;
                    bestReward = reward;
                    bestPath = path;
                }
                System.err.println("Moves left: " + movesLeft + ", Distance: " + distance);
            }
            
            if (bestPath != null && !bestPath.isEmpty()) {
                System.err.println("Agent " + agentId + ": Best Path to Reward: " + bestPath);
                setPath(agentId, bestPath); // Assign the path to the agent
            }

        }

        // Calculate shortest path cost
        private List<Location> aStarPath(Location start, Location goal, int agentId) {
            int id = agentId;
            PriorityQueue<Node> openList = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost)); // Priority queue data stores nodes (the smallest fCost) is dequeued first.
            Set<Location> closedList = new HashSet<>();                      // comparator sorts elements in ascending order based on their fcost
            Map<Location, Node> allNodes = new HashMap<>();

            // Initialize the start node
            Node startNode = new Node(start, null, 0, heuristic(start, goal));
            openList.add(startNode);
            allNodes.put(start, startNode);

            while (!openList.isEmpty()) {
                Node current = openList.poll();

                // If goal is reached, reconstruct the path
                if (current.location.equals(goal)) {
                    List<Location> path = new ArrayList<>();
                    while (current != null) {
                        if (!current.location.equals(start)) { // Skip adding the start location
                            path.add(0, current.location); // Add locations to the front
                        }
                        current = current.parent;
                    }
                    return path; // Return the full path
                }

                closedList.add(current.location);

                // Explore neighbors
                for (Location neighbor : getNeighbors(current.location)) {
                    if (closedList.contains(neighbor) || hasObject(OBSTACLE, neighbor) || isAgentAtLocation(neighbor) || isNonTargetReward(neighbor, id)) {
                        continue;
                    }

                    double tentativeGCost = current.gCost + 1; // Assume uniform cost for moving
                    Node neighborNode = allNodes.getOrDefault(neighbor, new Node(neighbor, null, Double.MAX_VALUE, heuristic(neighbor, goal)));

                    if (tentativeGCost < neighborNode.gCost) {
                        neighborNode.gCost = tentativeGCost;
                        neighborNode.fCost = tentativeGCost + heuristic(neighbor, goal);
                        neighborNode.parent = current;
        
                        if (!openList.contains(neighborNode)) {
                            openList.add(neighborNode);
                        }
                        allNodes.put(neighbor, neighborNode);
                    }
                }
            }
            return null; // No path found
        }

        // Check if an agent is at a specific location
        private boolean isAgentAtLocation(Location loc) {
            for (int i = 0; i < NUM_AGENTS; i++) {
                if (getAgPos(i).equals(loc)) {
                    return true;
                }
            }
            return false;
        }

        // Check if a location is a non-target reward
        private boolean isNonTargetReward(Location loc, int agentId) {
            // Check if the location has a reward and is not the target of the agent
            for (int rewardType : new int[]{BLUE, GREEN, YELLOW, PURPLE}) {
                if (hasObject(rewardType, loc)) {
                    // Get the current target reward for the agent
                    List<Location> targetPath = getPath(agentId);
                    if (targetPath != null && !targetPath.isEmpty()) {
                        Location targetReward = targetPath.get(targetPath.size() - 1); // Last location in the path is the target
                        if (!loc.equals(targetReward)) {
                            return true; // It's a non-target reward
                        }                        
                    }
                }
            }
            return false; // It's not a non-target reward
        }

        // Heuristic function
        private double heuristic(Location a, Location b) {
            return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
        }

        // Get valid neighbors
        private List<Location> getNeighbors(Location loc) {
            List<Location> neighbors = new ArrayList<>();
            int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

            for (int[] dir : directions) {
                int newX = loc.x + dir[0];
                int newY = loc.y + dir[1];

                if (newX >= 0 && newX < getWidth() && newY >= 0 && newY < getHeight()) {
                    neighbors.add(new Location(newX, newY));
                }
            }
            return neighbors;
        }

        // Node class
        private static class Node {
            Location location;
            Node parent;
            double gCost; // Cost from start to current node
            double fCost; // Total cost (gCost + heuristic)

            Node(Location location, Node parent, double gCost, double fCost) {
                this.location = location;
                this.parent = parent;
                this.gCost = gCost;
                this.fCost = gCost + fCost;
            }
        }

        // Changed collectReward to be more compact/readable
        void collectReward(int agentId) {
            Location explorer = getAgPos(agentId);

            // Define reward types and values in a map
            Map<Integer, Double> rewardValues = Map.of(
                BLUE, 0.6,
                GREEN, 0.8,
                YELLOW, 0.3,
                PURPLE, 0.2
            );
            
            for (Map.Entry<Integer, Double> entry : rewardValues.entrySet()) {
                int rewardType = entry.getKey();
                double rewardValue = entry.getValue();
        
                if (hasObject(rewardType, explorer)) {
                    // Update score and log
                    double score = agentScores.get(agentId);
                    score += rewardValue;
                    setScore(agentId, score);

                    actionLog.add("Collected reward " + rewardType + " at (" + explorer.x + ", " + explorer.y + "). New score: " + score);
        
                    // Remove old reward and place a new one
                    remove(rewardType, explorer);
                    add(rewardType, getRandomEmptyLocation());
                    break;
                }
            }
        }

        void printResults() {
            double score = getScore(0) + getScore(1);
            System.out.println("Maximum moves reached. Exploration over!");
            System.out.printf("Score of Agent 0 is: %.2f%n", getScore(0));
            System.out.printf("Score of Agent 1 is: %.2f%n", getScore(1));
            System.out.printf("Final score is: %.2f%n", score);
                
            // Add final score to log
            actionLog.add("Run complete. Final score: " + score);
            saveLogToFile(); // Save the log to a file
        }
        
        void saveLogToFile() {
            String fileName = "run_results.txt";
            try (FileWriter writer = new FileWriter(fileName, true)) { // Append to file
                for (String log : actionLog) {
                    writer.write(log + System.lineSeparator());
                }
                writer.write("---------- End of Run ----------" + System.lineSeparator());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class RewardsView extends GridWorldView {

        public RewardsView(RewardsModel model) {
            super(model, "Explore World", 600);
            defaultFont = new Font("Arial", Font.BOLD, 14); // change default font
            setVisible(true);
            repaint();
        }

        // Draw application objects
        public void draw(Graphics g, int x, int y, int object) {
            switch (object) {
            case RewardsModel.BLUE:
                drawBlock(g, x, y, Color.blue);
                break;
            case RewardsModel.GREEN:
                drawBlock(g, x, y, Color.green);
                break;
            case RewardsModel.YELLOW:
                drawBlock(g, x, y, Color.yellow);
                break;
            case RewardsModel.PURPLE:
                drawBlock(g, x, y, Color.magenta);
                break;
            case RewardsModel.OBSTACLE:
                drawObstacle(g, x, y);
                break;
            }
        }

        public void drawAgent(Graphics g, int x, int y, Color c, int id) {
            c = Color.gray; // Set agent color to gray
            super.drawAgent(g, x, y, c, -1);
            g.setColor(Color.white);
            super.drawString(g, x, y, defaultFont, "A" + id); // Label agent with its ID
        }

        private void drawBlock(Graphics g, int x, int y, Color color) {
            g.setColor(color);
            g.fillRect(x * cellSizeW + 15, y * cellSizeH + 15, cellSizeW - 30, cellSizeH - 30);
        }
    
        public void drawGrid(Graphics g) {
            // Access the model
            RewardsModel model = (RewardsModel) this.model;
        
            // Custom grid-drawing logic
            int gridSize = model.getWidth();
            int cellSize = 600 / gridSize;   // Adjust cell size based on view size (600x600)
        
            // Draw grid lines
            for (int i = 0; i <= gridSize; i++) {
                // Vertical lines
                g.drawLine(i * cellSize, 0, i * cellSize, 600);
                // Horizontal lines
                g.drawLine(0, i * cellSize, 600, i * cellSize);
            }
        }
    }
}
