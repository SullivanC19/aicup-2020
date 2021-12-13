import model.*;
import java.util.*;

public class MyStrategy {
    public static final int PROJECT_ENTITY_ID = -1;

    public static final double PERCENT_OF_ATTACKERS_RANGED_UNITS = 0.5;
    public static final double PERCENT_OF_ATTACKERS_MELEE_UNITS = 0.5;
    public static final int BUILDERS_GOAL = 60;

    public static final int NUM_HOUSE_BUILDERS = 3;
    public static final int MAX_TOTAL_HOUSE_DIST_FROM_HOUSE_BUILDERS = 30;
    public static final int MAX_NUM_HOUSE_PROJECTS = 2;
    public static final int NUM_ENEMY_TARGETS = 5;
    public static final Vec2Int ATTACKER_MEETING_POINT = new Vec2Int(25, 25); // not used

    private int myId = -1;
    private Map<EntityType, EntityProperties> entityProperties = null;

    private PlayerView playerView = null;

    private Entity[][] entityMap = null;
    private int[][] distFromOtherMapCorners = null;

    private EnumMap<EntityType, List<Entity>> myEntities = new EnumMap<>(EntityType.class);
    private EnumMap<EntityType, List<Entity>> enemyEntities = new EnumMap<>(EntityType.class);
    private List<Entity> resources = new ArrayList<>();
    private List<Entity> buildProjects = new ArrayList<>();
    private List<Entity> repairProjects = new ArrayList<>();

    private Map<Integer, Vec2Int> builderTargetPositions = new HashMap<>();
    private Map<Integer, Vec2Int> attackersToTargetPositions = new HashMap<>();

    private int populationAvailable = 0;
    private int populationUsed = 0;


    // HELPER METHODS

    private boolean isMyEntity(Entity entity) {
        return entity.getPlayerId() != null && entity.getPlayerId() == myId;
    }

    private int getMyResources() {
        return playerView.getPlayers()[myId - 1].getResource();
    }

    private EntityProperties getEntityProperties(Entity entity) {
        return entityProperties.get(entity.getEntityType());
    }

    private boolean isRepairable(EntityType entityType) {
        for (EntityType type : entityProperties.get(EntityType.BUILDER_UNIT).getRepair().getValidTargets()) {
            if (type == entityType) {
                return true;
            }
        }
        return false;
    }

    private boolean isAttacker(EntityType entityType) {
        EntityProperties properties = entityProperties.get(entityType);
        return properties.getAttack() != null && properties.isCanMove() && entityType != EntityType.BUILDER_UNIT;
    }

    private Vec2Int getOrigin() {
        return new Vec2Int(0, 0);
    }

    private int getPositionHash(Vec2Int position) {
        return position.getX() + position.getY() * playerView.getMapSize();
    }

    private boolean isAdjacent(Vec2Int position, Vec2Int targetPosition, int targetSize) {
        int x = position.getX();
        int y = position.getY();
        int x1 = targetPosition.getX();
        int y1 = targetPosition.getY();
        if (x == x1 - 1 || x == x1 + targetSize) {
            return y1 <= y && y < y1 + targetSize;
        } else if (x1 <= x && x < x1 + targetSize) {
            return y == y1 - 1 || y == y1 + targetSize;
        }
        return false;
    }

    private int dist(Vec2Int pos1, Vec2Int pos2) {
        return Math.abs(pos1.getX() - pos2.getX()) + Math.abs(pos1.getY() - pos2.getY());
    }

    private Entity getClosestEntityToOrigin(List<Entity> entities) {
        int closestDistFromOrigin = Integer.MAX_VALUE;
        Entity closestEntityToOrigin = null;
        for (Entity entity : entities) {
            int distFromOrigin = dist(entity.getPosition(), getOrigin());
            if (distFromOrigin < closestDistFromOrigin) {
                closestEntityToOrigin = entity;
                closestDistFromOrigin = distFromOrigin;
            }
        }
        return closestEntityToOrigin;
    }

    private boolean isProjectEntity(Entity entity) {
        return entity.getId() == PROJECT_ENTITY_ID;
    }

    private boolean isValidCoords(int x, int y) {
        return x >= 0 && y >= 0 && x < playerView.getMapSize() && y < playerView.getMapSize();
    }

    private boolean isValidPosition(Vec2Int position) {
        return isValidCoords(position.getX(), position.getY());
    }

    private List<Vec2Int> getAdjacentPositions(Vec2Int position, int size) {
        List<Vec2Int> adjacentPositions = new ArrayList<>();
        int x = position.getX();
        int y = position.getY();
        for (int diff = 0; diff < size; diff++) {
            adjacentPositions.add(new Vec2Int(x + diff, y - 1));
            adjacentPositions.add(new Vec2Int(x - 1, y + diff));
            adjacentPositions.add(new Vec2Int(x + diff, y + size));
            adjacentPositions.add(new Vec2Int(x + size, y + diff));
        }
        return adjacentPositions;
    }


    // SETUP METHODS

    private int getExpectedNumBuilders() {
        return Math.min(Math.max(MIN_BUILDERS, (int)Math.ceil(populationAvailable * PERCENT_OF_UNITS_BUILDERS)), MAX_BUILDERS);
    }

    private int getExpectedNumRangedUnits() {
        return (int)Math.ceil((populationAvailable - getExpectedNumBuilders()) * PERCENT_OF_ATTACKERS_RANGED_UNITS);
    }

    private int getExpectedNumMeleeUnits() {
        return (int)Math.ceil((populationAvailable - getExpectedNumBuilders()) * PERCENT_OF_ATTACKERS_MELEE_UNITS);
    }

    private void getAllEntities(Entity[] entities) {
        for (Entity entity : entities) {
            EntityType entityType = entity.getEntityType();
            EntityProperties properties = entityProperties.get(entityType);
            if (isMyEntity(entity)) {
                if (entity.getHealth() == properties.getMaxHealth()) {
                    populationAvailable += properties.getPopulationProvide();
                }
                populationUsed += properties.getPopulationUse();
                if (!myEntities.containsKey(entityType)) {
                    myEntities.put(entityType, new ArrayList<Entity>());
                }
                myEntities.get(entityType).add(entity);

                if (isRepairable(entityType)) {
                    for (int i = 0; i < projects.size(); ++i) {
                        if (dist(entity.getPosition(), projects.get(i).getPosition()) == 0 &&
                            entityType == projects.get(i).getEntityType()) {
                            projects.set(i, entity);
                        }
                    }
                }
            } else if (entityType == EntityType.RESOURCE) {
                resources.add(entity);
            } else {
                if (!enemyEntities.containsKey(entityType)) {
                    enemyEntities.put(entityType, new ArrayList<Entity>());
                }
                enemyEntities.get(entityType).add(entity);
            }

            Vec2Int entityPos = entity.getPosition();
            int x = entityPos.getX();
            int y = entityPos.getY();
            entityMap[x][y] = entity;
            int size = properties.getSize();
            if (size > 1) {
                for (int xDiff = 0; xDiff < size; xDiff++) {
                    for (int yDiff = 0; yDiff < size; yDiff++) {
                        entityMap[x + xDiff][y + yDiff] = entity;
                    }
                }
            }
        }

        for (int i = 0; i < projects.size(); ++i) {
            Entity project = projects.get(i);
            EntityProperties properties = getEntityProperties(project);
            if (project.getHealth() >= properties.getMaxHealth() ||
                (isProjectEntity(project) && getOccupiedLevel(project.getPosition().getX(),
                                                              project.getPosition().getY(),
                                                              properties.getSize()) == 2)) {
                projects.remove(i);
                i--;
            }
        }

        // for (Entity project : projects) {
        //     int x = project.getPosition().getX();
        //     int y = project.getPosition().getY();
        // }
    }
    
    class Point implements Comparable<Point> {
        int x, y, dist;
        public Point(int x, int y, int dist) {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }
        public int compareTo(Point o) {
            if (dist != o.dist) {
                return dist - o.dist;
            }
            return (x * 1000 + y) - (o.x * 1000 + o.y);
        }
        public boolean equals(Object o) {
            return x == ((Point)o).x && y == ((Point)o).y;
        }
    }

    private void getOtherMapCornerDistanceGrid() {
        int mapSize = playerView.getMapSize();
        boolean[][] visited = new boolean[mapSize][mapSize];
        distFromOtherMapCorners = new int[mapSize][mapSize];
        PriorityQueue<Point> toVisit = new PriorityQueue<Point>();
        Point[] otherMapCorners = new Point[] {new Point(mapSize - 1, 0, 0),
                                               new Point(0, mapSize - 1, 0),
                                               new Point(mapSize - 1, mapSize - 1, 0)};
        int[][] diff = new int[][] {
            new int[] {0, 1},
            new int[] {0, -1},
            new int[] {1, 0},
            new int[] {-1, 0}
        };
        for (Point p : otherMapCorners) {
            toVisit.add(p);
            distFromOtherMapCorners[p.x][p.y] = p.dist;
        }
        while (!toVisit.isEmpty()) {
            Point node = toVisit.remove();
            visited[node.x][node.y] = true;

            for (int dir = 0; dir < 4; dir++) {
                int x = node.x + diff[dir][0];
                int y = node.y + diff[dir][1];
                if (isValidCoords(x, y) && !visited[x][y]) {
                    // resource health is 30, unit attack is 5, 30/5 + 1 (for final move) is 7
                    int distDiff = entityMap[x][y] != null && entityMap[x][y].getEntityType() == EntityType.RESOURCE ? 7 : 1;
                    Point child = new Point(x, y, node.dist + distDiff);
                    boolean alreadyAdded = toVisit.contains(child);
                    if (!alreadyAdded || child.dist < distFromOtherMapCorners[x][y]) {
                        if (alreadyAdded) {
                            toVisit.remove(new Point(x, y, distFromOtherMapCorners[x][y]));
                        }
                        distFromOtherMapCorners[x][y] = child.dist;
                        toVisit.add(child);
                    }
                }
            }
        }
        // if (playerView.getCurrentTick() == 200) {
        //     for (int[] a : distFromOtherMapCorners) {
        //         for (int b : a) {
        //             System.out.print(b + " ");
        //         }
        //         System.out.println();
        //     }
        // }
    }

    private int getOccupiedLevel(int x, int y, int size) {
        // 0 for nothing, 1 for might move eventually, and 2 for occupied by something that can't move
        if (!isValidCoords(x, y)) return 0;

        if (size == 1) {
            if (entityMap[x][y] == null) return 0;
            if (entityProperties.get(entityMap[x][y].getEntityType()).isCanMove()) return 1;
            return 2;
        }

        int maxOccupiedLevel = 0;
        for (int xDiff = 0; xDiff < size; ++xDiff) {
            for (int yDiff = 0; yDiff < size; ++yDiff) {
                maxOccupiedLevel = Math.max(maxOccupiedLevel, getOccupiedLevel(x + xDiff, y + yDiff, 1));
                if (maxOccupiedLevel == 2) {
                    return 2;
                }
            }
        }
        return maxOccupiedLevel;
    }

    private boolean isValidHousePosition(Vec2Int position) {
        if (!isValidPosition(position)) return false;

        int houseSize = entityProperties.get(EntityType.HOUSE).getSize();
        int x = position.getX();
        int y = position.getY();
        if (x == 0) {
            return y % houseSize == 0 && getOccupiedLevel(x, y, houseSize) == 0;
        } else if (y == 0) {
            return (x - 1) % houseSize == 0 && getOccupiedLevel(x, y, houseSize) == 0;
        } else if (x > houseSize && y > houseSize) {
            return getOccupiedLevel(x - 1, y - 1, houseSize + 2) <= 1;
        }
        return false;
    }

    private Vec2Int getClosestValidHousePositionToHouseBuilders() {
        if (myEntities.get(EntityType.BUILDER_UNIT).size() < NUM_HOUSE_BUILDERS) return null;

        List<Entity> builders = new ArrayList<>();
        List<Entity> houseBuilders = new ArrayList<>();
        builders.addAll(myEntities.get(EntityType.BUILDER_UNIT));
        for (int i = 0; i < NUM_HOUSE_BUILDERS; i++) {
            Entity houseBuilder = getClosestEntityToOrigin(builders);
            builders.remove(houseBuilder);
            houseBuilders.add(houseBuilder);
        }

        int minDist = Integer.MAX_VALUE;
        Vec2Int housePosition = null;
        for (int l = 0; l < playerView.getMapSize() * 2 / 3; l++) {
            for (int x = 0; x <= l; x++) {
                Vec2Int position = new Vec2Int(x, l - x);
                if (isValidHousePosition(position)) {
                    int totDist = 0;
                    for (Entity houseBuilder : houseBuilders) {
                        totDist += dist(houseBuilder.getPosition(), position);
                    }
                    if (totDist < minDist) {
                        housePosition = position;
                        minDist = totDist;
                    }
                }
            }
        }
        return housePosition;
    }

    private List<Vec2Int> getTargetPositions(List<Entity> targetEntities) {
        int mapSize = playerView.getMapSize();
        int[][] positionFreq = new int[mapSize][mapSize];
        for (Entity entity : targetEntities) {
            int entitySize = getEntityProperties(entity).getSize();
            for (Vec2Int adjacentPosition : getAdjacentPositions(entity.getPosition(), entitySize)) {
                if (isValidPosition(adjacentPosition) && getOccupiedLevel(adjacentPosition.getX(), adjacentPosition.getY(), 1) <= 1) {
                    positionFreq[adjacentPosition.getX()][adjacentPosition.getY()]++;
                }
            }
        }

        List<Vec2Int> targetPositions = new ArrayList<>();
        for (int x = 0; x < mapSize; x++) {
            for (int y = 0; y < mapSize; y++) {
                if (positionFreq[x][y] > 0 && positionFreq[x][y] < 3) {
                    targetPositions.add(new Vec2Int(x, y));
                }
            }
        }
        return targetPositions;
    }

    class Edge implements Comparable<Edge> {
        public int dist;
        public Entity builder;
        public Vec2Int targetPosition;
        public Edge(Entity builder, Vec2Int targetPosition) {
            this.builder = builder;
            this.targetPosition = targetPosition;
            this.dist = dist(builder.getPosition(), targetPosition);
        }
        public int compareTo(MyStrategy.Edge o) {
            return this.dist - o.dist;
        }
    }

    private void assignBuildersToPositions(List<Vec2Int>[] positionLists, Integer[] assignLimits) {
        List<Entity> builders = new ArrayList<>();
        builders.addAll(myEntities.getOrDefault(EntityType.BUILDER_UNIT, new ArrayList<>()));
        for (int i = 0; i < positionLists.length; i++) {
            List<Vec2Int> positions = positionLists[i];
            List<Edge> edges = new ArrayList<>();
            for (Entity builder : builders) {
                if (builderTargetPositions.containsKey(builder.getId())) continue;
                for (Vec2Int pos : positions) {
                    edges.add(new Edge(builder, pos));
                }
            }
            Collections.sort(edges);

            Set<Integer> assignedPositions = new HashSet<>();
            Set<Entity> assignedBuilders = new HashSet<>();
            for (Edge e : edges) {
                if (assignLimits[i] != null && assignedBuilders.size() >= assignLimits[i]) break;

                int builderId = e.builder.getId();
                int positionHash = getPositionHash(e.targetPosition);
                if (builderTargetPositions.containsKey(builderId) || assignedPositions.contains(positionHash)) continue;
                builderTargetPositions.put(builderId, e.targetPosition);
                assignedPositions.add(positionHash);
                assignedBuilders.add(e.builder);
            }
            builders.removeAll(assignedBuilders);
        }
    }

    private List<Entity> getAllEnemies() {
        List<Entity> allEnemies = new ArrayList<>();
        for (Map.Entry<EntityType, List<Entity>> enemyTypeListEntry : enemyEntities.entrySet()) {
            allEnemies.addAll(enemyTypeListEntry.getValue());
        }
        return allEnemies;
    }

    private List<Entity> getAllAttackers() {
        List<Entity> allAttackers = new ArrayList<>();
        for (Map.Entry<EntityType, List<Entity>> myEntityTypeListEntry : myEntities.entrySet()) {
            if (isAttacker(myEntityTypeListEntry.getKey())) {
                allAttackers.addAll(myEntityTypeListEntry.getValue());
            }
        }
        return allAttackers;
    }

    private void assignAttackersToEnemies() {
        Entity[] targets = new Entity[NUM_ENEMY_TARGETS];
        List<Entity> potentialEnemyTargets = getAllEnemies();
        for (int i = 0; i < NUM_ENEMY_TARGETS; i++) {
            targets[i] = getClosestEntityToOrigin(potentialEnemyTargets);
            potentialEnemyTargets.remove(targets[i]);
        }

        for (Entity attacker : getAllAttackers()) {
            int minDist = Integer.MAX_VALUE;
            for (int i = 0; i < NUM_ENEMY_TARGETS; i++) {
                if (targets[i] != null) {
                    int newDist = dist(attacker.getPosition(), targets[i].getPosition());
                    if (newDist < minDist) {
                        attackersToTargetPositions.put(attacker.getId(), targets[i].getPosition());
                        minDist = newDist;
                    }
                }
            }
        }
    }

    private boolean houseProjectExists(Vec2Int houseProjectPosition) {
        for (Entity project : projects) {
            if (dist(project.getPosition(), houseProjectPosition) == 0) {
                return true;
            }
        }
        return false;
    }

    private void createHouseProjects() {
        int houseCost = entityProperties.get(EntityType.HOUSE).getInitialCost();
        if (populationUsed == populationAvailable && getMyResources() >= houseCost * (projects.size() + 1) && projects.size() < MAX_NUM_HOUSE_PROJECTS) {
            Vec2Int houseProjectPosition = getClosestValidHousePositionToHouseBuilders();
            if (houseProjectPosition != null) {
                Entity houseProject = new Entity(PROJECT_ENTITY_ID, null, EntityType.HOUSE, houseProjectPosition, 0, false);
                if (!houseProjectExists(houseProjectPosition)) {
                    projects.add(houseProject);
                }
            }
        }
    }


    // UNIT ACTIONS

    private EntityAction getAutoBuildRepairAction(Entity builder) {
        for (Entity project : projects) {
            if (isAdjacent(builder.getPosition(), project.getPosition(), getEntityProperties(project).getSize())) {
                return new EntityAction(null,
                                        new BuildAction(project.getEntityType(),
                                                        project.getPosition()),
                                        null,
                                        // check if the project has been built (thereby making it no longer a project)
                                        (isProjectEntity(project)) ? null : new RepairAction(project.getId()));
            }
        }
        return new EntityAction();
    }

    private EntityAction getBuilderUnitAction(Entity builder) {
        // move to target resource if one exists
        Vec2Int targetPosition = builderTargetPositions.get(builder.getId());
        if (targetPosition != null) {
            // don't want to accidentally auto attack or build anything else before getting to position
            if (dist(builder.getPosition(), targetPosition) > 0) {
                return new EntityAction(new MoveAction(targetPosition,
                                                       true,
                                                       true),
                                        null,
                                        null,
                                        null);
            }
            // auto build or repair if a project is the target
            EntityAction builderAction = getAutoBuildRepairAction(builder);
            // auto attack resource if a resource is the target
            builderAction.setAttackAction(new AttackAction(null, new AutoAttack(0, new EntityType[] {EntityType.RESOURCE})));
            return builderAction;
        }

        // otherwise, explore!
        Vec2Int oppositeEndOfMap = new Vec2Int(playerView.getMapSize() - 1, playerView.getMapSize() - 1);
        return new EntityAction(new MoveAction(oppositeEndOfMap,
                                               true,
                                               false),
                                null,
                                null,
                                null);
    }

    private EntityAction getAttackerAction(Entity entity) {
        // move to target enemy if one exists
        Vec2Int targetEnemyPosition = attackersToTargetPositions.get(entity.getId());
        int sightRange = getEntityProperties(entity).getSightRange();
        if (targetEnemyPosition != null) {
            return new EntityAction(new MoveAction(targetEnemyPosition,
                                                   true,
                                                   true),
                                    null,
                                    new AttackAction(null, new AutoAttack(sightRange, new EntityType[0])),
                                    null);
        }

        // otherwise, go to constant meeting point
        return new EntityAction(new MoveAction(ATTACKER_MEETING_POINT,
                                               true,
                                               false),
                                null,
                                null,
                                null);
    }

    private EntityAction getTurretAction(Entity entity) {
        return new EntityAction(null, null, new AttackAction(null, new AutoAttack(0, new EntityType[0])), null);
    }


    // BASE ACTIONS

    private EntityAction getBuilderBaseAction(Entity entity) {
        List<Entity> builderUnits = myEntities.getOrDefault(EntityType.BUILDER_UNIT, new ArrayList<>());
        if (builderUnits.size() >= getExpectedNumBuilders()) return new EntityAction();

        int baseSize = entityProperties.get(EntityType.BUILDER_BASE).getSize();
        Vec2Int buildPosition = new Vec2Int(entity.getPosition().getX() + baseSize,
                                            entity.getPosition().getY() + baseSize - 1);
        return new EntityAction(null,
                                new BuildAction(EntityType.BUILDER_UNIT,
                                                buildPosition),
                                null,
                                null);
    }

    private EntityAction getRangedBaseAction(Entity entity) {
        List<Entity> rangedUnits = myEntities.getOrDefault(EntityType.RANGED_UNIT, new ArrayList<>());
        if (rangedUnits.size() >= getExpectedNumRangedUnits()) return new EntityAction();
        
        int baseSize = entityProperties.get(EntityType.RANGED_BASE).getSize();
        Vec2Int buildPosition = new Vec2Int(entity.getPosition().getX() + baseSize,
                                            entity.getPosition().getY() + baseSize - 1);
        return new EntityAction(null,
                                new BuildAction(EntityType.RANGED_UNIT,
                                                buildPosition),
                                null,
                                null);
    }

    private EntityAction getMeleeBaseAction(Entity entity) {
        List<Entity> meleeUnits = myEntities.getOrDefault(EntityType.MELEE_UNIT, new ArrayList<>());
        if (meleeUnits.size() >= getExpectedNumMeleeUnits()) return new EntityAction();

        int baseSize = entityProperties.get(EntityType.MELEE_BASE).getSize();
        Vec2Int buildPosition = new Vec2Int(entity.getPosition().getX() + baseSize,
                                            entity.getPosition().getY() + baseSize - 1);
        return new EntityAction(null,
                                new BuildAction(EntityType.MELEE_UNIT,
                                                buildPosition),
                                null,
                                null);
    }


    // GET ACTION

    private EntityAction getEntityAction(Entity entity) {
        switch(entity.getEntityType()) {
            case BUILDER_UNIT:
                return getBuilderUnitAction(entity);
            case RANGED_UNIT:
                return getAttackerAction(entity);
            case MELEE_UNIT:
                return getAttackerAction(entity);
            case TURRET:
                return getTurretAction(entity);
            case BUILDER_BASE:
                return getBuilderBaseAction(entity);
            case RANGED_BASE:
                return getRangedBaseAction(entity);
            case MELEE_BASE:
                return getMeleeBaseAction(entity);
            default:
                return null;
        }
    }

    private void resetForNewTick(PlayerView playerView) {
        if (entityProperties == null) entityProperties = playerView.getEntityProperties();
        if (myId == -1) myId = playerView.getMyId();
        this.playerView = playerView;

        int mapSize = playerView.getMapSize();
        entityMap = new Entity[mapSize][mapSize];

        populationAvailable = 0;
        populationUsed = 0;
    
        myEntities.clear();
        enemyEntities.clear();
        resources.clear();
    
        builderTargetPositions.clear();
        attackersToTargetPositions.clear();
    }

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        Entity[] entities = playerView.getEntities();

        // reset lists, maps, variables, etc
        resetForNewTick(playerView);

        // update entity lists
        getAllEntities(entities);
        getOtherMapCornerDistanceGrid();

        // System.out.println("builders: " + myEntities.getOrDefault(EntityType.BUILDER_UNIT, new ArrayList<>()).size() + "/" + getExpectedNumBuilders());
        // System.out.println("ranged units: " + myEntities.getOrDefault(EntityType.RANGED_UNIT, new ArrayList<>()).size() + "/" + getExpectedNumRangedUnits());
        // System.out.println("melee units: " + myEntities.getOrDefault(EntityType.MELEE_UNIT, new ArrayList<>()).size() + "/" + getExpectedNumMeleeUnits());

        // create new projects
        createHouseProjects();

        // find target positions and assign units to them
        List<Vec2Int> targetProjectPositions = getTargetPositions(projects);
        List<Vec2Int> targetResourcePositions = getTargetPositions(resources);
        assignBuildersToPositions(new List[] {targetProjectPositions, targetResourcePositions},
                                  new Integer[] {NUM_HOUSE_BUILDERS * projects.size(), null});
        assignAttackersToEnemies();

        // get entity actions
        Map<Integer, EntityAction> entityActions = new HashMap<>();
        for (Entity entity : entities) {
            if (!isMyEntity(entity)) continue;
            EntityAction entityAction = getEntityAction(entity);
            if (entityAction != null) {
                entityActions.put(entity.getId(), entityAction);
            }
        }
        return new Action(entityActions);
    }


    // DEBUG

    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
        debugInterface.send(new DebugCommand.Clear());
        debugInterface.getState();
    }
}