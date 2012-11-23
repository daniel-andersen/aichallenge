import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MyBot extends Bot {
    private static final Logger logger = Logger.getLogger(MyBot.class.getName());

    private int turn = 0;
    private Ants ants;
    private Set<MyAnt> myAnts;
    private Set<MapElem> myFood;
    private Set<MapElem> myFoodUncovered;
    private Set<MapElem> myHills;
    private Set<MapElem> myEnemyAnts;
    private Set<MapElem> myEnemyHills;
    private Set<MapElem> tilesToExplore;

    private boolean collisionMap[][];
    private boolean exploredMap[][];
    private boolean visibleMap[][];
    private boolean razeMode;

    private PathMap[] pathMapFood = new PathMap[2];
    private PathMap[] pathMapLandToExplore = new PathMap[2];
    private PathMap pathMapMyHills;
    private PathMap pathMapEnemies;
    private PathMap pathMapEnemyHills;
    private PathMap pathMapAttackerGroup;

    private Tile[][] pathTiles;

    public static void main(String[] args) throws IOException {
        FileHandler fh = new FileHandler("outputLog.log");
        fh.setFormatter(new SimpleFormatter());
        logger.addHandler(fh);
        new MyBot().readSystemInput();
    }

    public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2, int attackRadius2, int spawnRadius2) {
        super.setup(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);
        ants = getAnts();
        initializePathAlgorithms();
        collisionMap = new boolean[ants.getRows()][ants.getCols()];
        exploredMap = new boolean[ants.getRows()][ants.getCols()];
        visibleMap = new boolean[ants.getRows()][ants.getCols()];
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(exploredMap[i], false);
            Arrays.fill(visibleMap[i], false);
        }
        tilesToExplore = new HashSet<MapElem>();
    }

    @Override
    public void doTurn() {
		if (turn > 2000) {
			return;
		}
        ants = getAnts();
        getObjectsOnMap();
        razeMode = myAnts.size() > myEnemyAnts.size() * 2;
        updateLandToExplore();
        updateVisibility();
        calculatePahtMaps();
        delegateAnts();
        razeEnemy();
        defendOwnHills();
        updateBattlingAnts();
        issueCommands();
        turn++;
    }

    private void updateLandToExplore() {
        for (MyAnt ant : myAnts) {
            for (int i = 0; i < 2; i++) {
                Tile tileToExplore = pathMapLandToExplore[i].destInPathMap(ant.tile);
                if (tileToExplore == null) {
                    continue;
                }
                int dist = pathMapLandToExplore[i].distInPathMap(ant.tile);
                if (dist < 5) {
                    for (MapElem elem : tilesToExplore) {
                        if (elem.tile.equals(tileToExplore)) {
                            tilesToExplore.remove(elem);
                            break;
                        }
                    }
                }
            }
        }
        if (tilesToExplore.size() < 128) {
            for (int i = 0; i < 20; i++) {
                int col = (int) (Math.random() * ants.getCols());
                int row = (int) (Math.random() * ants.getRows());
                MapElem elem = new MapElem(new Tile(row, col));
                if (ants.getIlk(elem.tile).isPassable()) {
                    tilesToExplore.add(elem);
                    break;
                }
            }
        }
    }

    private void updateVisibility() {
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (ants.isVisible(pathTiles[i][j])) {
                    visibleMap[i][j] = true;
                }
            }
        }
    }

    private void calculatePahtMaps() {
        pathMapFood[0].calculateMap(myFood);
        myFoodUncovered = getUncoveredElems(pathMapFood[0], myFood);
        pathMapFood[1].calculateMap(myFoodUncovered);
        pathMapEnemies.calculateMap(myEnemyAnts);
        pathMapMyHills.calculateMapIfNotFullyExplored(myHills, false);
        pathMapEnemyHills.calculateMapIfNotFullyExplored(myEnemyHills);
        pathMapLandToExplore[0].calculateMap(tilesToExplore);
        pathMapLandToExplore[1].calculateMap(getUncoveredElems(pathMapLandToExplore[0], tilesToExplore));
    }

    private Set<MapElem> getUncoveredElems(PathMap pathMap, Set<MapElem> mapElems) {
        Set<MapElem> remainingElems = new HashSet<MapElem>(mapElems);
        for (MyAnt ant : myAnts) {
            MapElem elem = pathMap.infoInPathMap(ant.tile);
            if (elem != null) {
                remainingElems.remove(elem);
            }
        }
        return remainingElems;
    }

    private void issueCommands() {
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(collisionMap[i], false);
        }
        for (MyAnt ant : myAnts) {
            collisionMap[ant.tile.getRow()][ant.tile.getCol()] = true;
        }
        for (MyAnt ant : myAnts) {
            exploredMap[ant.tile.getRow()][ant.tile.getCol()] = true;
            if (ant.order != null) {
                Tile dest = ants.getTile(ant.tile, ant.order);
                if (!collisionMap[dest.getRow()][dest.getCol()]) {
                    collisionMap[dest.getRow()][dest.getCol()] = true;
                    collisionMap[ant.tile.getRow()][ant.tile.getCol()] = false;
                    ants.issueOrder(ant.tile, ant.order);
                    continue;
                }
            }
            moveToAvoidCollision(ant);
        }
    }

    private void defendOwnHills() {
        final int MAX_DEFENDERS_PER_HILL = 5;
        final int ATTACKED_RADIUS = ants.getAttackRadius2() * 4;
        if (myHills.size() == 0) {
            return;
        }
        for (MapElem hill : myHills) {
            int enemyCount = 0;
            for (MapElem enemy : myEnemyAnts) {
                if (ants.getDistance(hill.tile, enemy.tile) < ATTACKED_RADIUS) {
                    enemyCount++;
                }
            }
            int numDefendersPerHill = Math.max(Math.min((myAnts.size() / 5) / myHills.size(), MAX_DEFENDERS_PER_HILL), enemyCount);
            if (numDefendersPerHill <= 0) {
                continue;
            }
            Set<MyAnt> defenders = new HashSet<MyAnt>();
            for (MyAnt ant : myAnts) {
                if (ants.getDistance(hill.tile, ant.tile) < ants.getAttackRadius2()) {
                    defenders.add(ant);
                }
            }
            if (defenders.size() >= numDefendersPerHill) {
                continue;
            }
            MyAnt nearestAnt = null;
            int nearestDist = Integer.MAX_VALUE;
            for (MyAnt ant : myAnts) {
                if (ants.getDistance(hill.tile, ant.tile) < ants.getAttackRadius2()) {
                    continue;
                }
                int dist = pathMapMyHills.distInPathMap(ant.tile);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestAnt = ant;
                }
            }
            if (nearestAnt != null) {
                nearestAnt.order = pathMapMyHills.getFirstMovementInPath(nearestAnt.tile);
            }
        }
    }

    private void razeEnemy() {
        if (!razeMode) {
            return;
        }
        for (MyAnt ant : myAnts) {
            if (ant.destPathMap == pathMapFood[0] || ant.destPathMap == pathMapFood[1]) {
                continue;
            }
            if (pathMapEnemyHills.destInPathMap(ant.tile) != null) {
                ant.order = pathMapEnemyHills.getFirstMovementInPath(ant.tile);
            } else if (pathMapEnemies.destInPathMap(ant.tile) != null) {
                ant.order = pathMapEnemies.getFirstMovementInPath(ant.tile);
            }
        }
    }

    private void moveToAvoidCollision(MyAnt ant) {
        Aim[] aim = new Aim[] {Aim.NORTH, Aim.EAST, Aim.SOUTH, Aim.WEST};
        for (int i = 0; i < 4; i++) {
            int j = (int) (Math.random() * 4.0f);
            Aim swap = aim[i];
            aim[i] = aim[j];
            aim[j] = swap;
        }
        for (int i = 0; i < 4; i++) {
            Tile dest = ants.getTile(ant.tile, aim[i]);
            if (!collisionMap[dest.getRow()][dest.getCol()]) {
                collisionMap[dest.getRow()][dest.getCol()] = true;
                ant.order = aim[i];
                ants.issueOrder(ant.tile, ant.order);
                return;
            }
        }
    }

    private void updateBattlingAnts() {
        if (razeMode) {
            return;
        }
        Set<MapElem> attackerGroups = new HashSet<MapElem>();
        for (MapElem elem : myEnemyAnts) {
            if (elem.ants.size() < 2) {
                continue;
            }
            int middleX = 0;
            int middleY = 0;
            int middleCount = 0;
            for (MyAnt ant : elem.ants) {
                int dist = pathMapEnemies.distInPathMap(ant.tile);
                if (dist < ants.getAttackRadius2() * 2) {
                    middleX += ant.tile.getCol();
                    middleY += ant.tile.getRow();
                    middleCount++;
                }
            }
            if (middleCount == 0) {
                continue;
            }
            middleX /= middleCount;
            middleY /= middleCount;
            attackerGroups.add(new MapElem(new Tile(middleY, middleX)));
        }
        pathMapAttackerGroup.calculateMap(attackerGroups);
        for (MapElem elem : myEnemyAnts) {
            if (elem.ants.size() < 2) {
                continue;
            }
            for (MyAnt ant : elem.ants) {
                int dist = pathMapEnemies.distInPathMap(ant.tile);
                if (dist >= ants.getAttackRadius2() * 2) {
                    continue;
                }
                ant.order = pathMapAttackerGroup.getFirstMovementInPath(ant.tile);
            }
        }
    }

    private void delegateAnts() {
        delegateAntsOnPathMap(pathMapFood[0], 32);
        restrictNumberOfAntsPerDestination(pathMapFood[0], myFood, 1);
        delegateAntsOnPathMap(pathMapFood[1], 32);
        restrictNumberOfAntsPerDestination(pathMapFood[1], myFoodUncovered, 1);
        delegateAntsOnPathMap(pathMapEnemies, 12);
        restrictNumberOfAntsPerDestination(pathMapEnemies, myEnemyAnts, 4);
        delegateAntsOnPathMap(pathMapEnemyHills, 16);
        restrictNumberOfAntsPerDestination(pathMapEnemyHills, myEnemyHills, 4);
        delegateAntsOnPathMap(pathMapLandToExplore[0], Integer.MAX_VALUE);
        splitAntsOnPathMap(pathMapLandToExplore, tilesToExplore);
        for (MyAnt ant : myAnts) {
            if (ant.destPathMap == null) {
                continue;
            }
            ant.order = ant.destPathMap.getFirstMovementInPath(ant.tile);
        }
    }

    private void delegateAntsOnPathMap(PathMap pathMap, int minDist) {
        for (MyAnt ant : myAnts) {
            if (ant.destPathMap != null) {
                continue;
            }
            if (pathMap.distInPathMap(ant.tile) > minDist) {
                continue;
            }
            MapElem elem = pathMap.infoInPathMap(ant.tile);
            if (elem == null) {
                continue;
            }
            ant.destPathMap = pathMap;
            elem.ants.add(ant);
        }
    }

    private void splitAntsOnPathMap(PathMap[] pathMaps, Set<MapElem> mapElems) {
        for (MapElem elem : mapElems) {
            if (elem.ants.size() == 0) {
                continue;
            }
            int halfDistance = 0;
            for (MyAnt ant : elem.ants) {
                halfDistance += pathMaps[0].distInPathMap(ant.tile);
            }
            halfDistance /= elem.ants.size();
            for (MyAnt ant : elem.ants) {
                if (pathMaps[0].distInPathMap(ant.tile) > halfDistance && pathMaps[1].destInPathMap(ant.tile) != null) {
                    ant.destPathMap = pathMaps[1];
                }
            }
        }
    }

    private void restrictNumberOfAntsPerDestination(PathMap pathMap, Set<MapElem> mapElems, int maxAnts) {
        for (MapElem elem : mapElems) {
            if (elem.ants.size() <= maxAnts) {
                continue;
            }
            Set<MyAnt> antsLeft = new HashSet<MyAnt>();
            Set<MyAnt> antsOrig = new HashSet<MyAnt>(elem.ants);
            for (int i = 0; i < maxAnts; i++) {
                int minDist = Integer.MAX_VALUE;
                MyAnt minAnt = null;
                for (MyAnt ant : antsOrig) {
                    int dist = pathMap.distInPathMap(ant.tile);
                    if (dist < minDist) {
                        minDist = dist;
                        minAnt = ant;
                    }
                }
                antsLeft.add(minAnt);
                antsOrig.remove(minAnt);
            }
            for (MyAnt ant : elem.ants) {
                ant.destPathMap = null;
            }
            for (MyAnt ant : antsLeft) {
                ant.destPathMap = pathMap;
            }
            elem.ants = antsLeft;
        }
    }

    private void getObjectsOnMap() {
        myAnts = new HashSet<MyAnt>();
        for (Tile ant : ants.getMyAnts()) {
            myAnts.add(new MyAnt(ant));
        }
        myFood = new HashSet<MapElem>();
        for (Tile food : ants.getFoodTiles()) {
            myFood.add(new MapElem(food));
        }
        myHills = new HashSet<MapElem>();
        for (Tile myHill : ants.getMyHills()) {
            myHills.add(new MapElem(myHill));
        }
        myEnemyAnts = new HashSet<MapElem>();
        for (Tile enemy : ants.getEnemyAnts()) {
            myEnemyAnts.add(new MapElem(enemy));
        }
        myEnemyHills = new HashSet<MapElem>();
        for (Tile enemyHill : ants.getEnemyHills()) {
            myEnemyHills.add(new MapElem(enemyHill));
        }
    }

    private void logMap() {
        String s = "Map in turn " + turn + ":\n";
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                Ilk ilk = ants.getIlk(new Tile(i, j));
                if (ilk == Ilk.LAND) {
                    if (visibleMap[i][j]) {
                        s += ".";
                    } else {
                        s += " ";
                    }
                } else {
                    s += ants.getIlk(new Tile(i, j));
                }
            }
            s += "\n";
        }
        logger.info(s);
    }



    /*
        PATH ALGORITHMS
        ---------------
     */

    private void initializePathAlgorithms() {
        pathTiles = new Tile[ants.getRows()][ants.getCols()];
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                pathTiles[i][j] = new Tile(i, j);
            }
        }
        pathMapFood[0] = new PathMap(1024 * 24);
        pathMapFood[1] = new PathMap(1024 * 24);
        pathMapMyHills = new PathMap(1024 * 1);
        pathMapEnemies = new PathMap(1024 * 16);
        pathMapEnemyHills = new PathMap(1024 * 8);
        pathMapLandToExplore[0] = new PathMap(1024 * 16);
        pathMapLandToExplore[1] = new PathMap(1024 * 16);
        pathMapAttackerGroup = new PathMap(256);
    }

    private class PathMap {
        public int[][] distMap;
        public Tile[][] destMap;
        public MapElem[][] infoMap;
        private PathElem[] pathQueue;
        private int queueIndex = 0;
        private int queueCount = 0;
        private int maxQueueLength;
        public boolean isFullyExplored = false;

        public PathMap(int maxQueueLength) {
            this.maxQueueLength = maxQueueLength;
            distMap = new int[ants.getRows()][ants.getCols()];
            destMap = new Tile[ants.getRows()][ants.getCols()];
            infoMap = new MapElem[ants.getRows()][ants.getCols()];
            pathQueue = new PathElem[maxQueueLength];

        }

        public int distInPathMap(Tile tile) {
            return distMap[tile.getRow()][tile.getCol()];
        }

        public Tile destInPathMap(Tile tile) {
            return destMap[tile.getRow()][tile.getCol()];
        }

        public MapElem infoInPathMap(Tile tile) {
            return infoMap[tile.getRow()][tile.getCol()];
        }

        public Aim getFirstMovementInPath(Tile source) {
            if (distMap[source.getRow()][source.getCol()] == Integer.MAX_VALUE) {
                return null;
            }
            int minDist = Integer.MAX_VALUE;
            Aim minAim = null;
            for (Aim aim : Aim.values()) {
                Tile tile = ants.getTile(source, aim);
                int dist = distMap[tile.getRow()][tile.getCol()];
                if (dist < minDist) {
                    minDist = dist;
                    minAim = aim;
                }
            }
            return minAim;
        }

        public void calculateMapIfNotFullyExplored(Set<MapElem> dest) {
            if (isFullyExplored) {
                return;
            }
            calculateMap(dest);
        }

        public void calculateMapIfNotFullyExplored(Set<MapElem> dest, boolean includeInvisibleTiles) {
            if (isFullyExplored) {
                return;
            }
            calculateMap(dest, includeInvisibleTiles);
        }

        public void calculateMap(Set<MapElem> dest) {
            calculateMap(dest, true);
        }

        public void calculateMap(Set<MapElem> dest, boolean includeInvisibleTiles) {
            for (int i = 0; i < ants.getRows(); i++) {
                Arrays.fill(distMap[i], Integer.MAX_VALUE);
                Arrays.fill(destMap[i], null);
                Arrays.fill(infoMap[i], null);
            }
            if (dest.size() == 0) {
                return;
            }
            queueIndex = 0;
            queueCount = 0;
            for (MapElem mapElem : dest) {
                if (!ants.getIlk(mapElem.tile).isPassable()) {
                    continue;
                }
                PathElem elem = new PathElem(mapElem.tile.getCol(), mapElem.tile.getRow(), 0);
                pathQueue[queueCount++] = elem;
                distMap[elem.y][elem.x] = elem.count;
                destMap[elem.y][elem.x] = mapElem.tile;
                infoMap[elem.y][elem.x] = mapElem;
            }
            calculateMapInternal(includeInvisibleTiles);
        }

        private void calculateMapInternal(boolean includeInvisibleTiles) {
            isFullyExplored = true;
            while (queueIndex < queueCount && queueCount < maxQueueLength - 4) {
                PathElem elem = pathQueue[queueIndex++];
                for (Aim aim: Aim.values()) {
                    Tile newTile = ants.getTile(pathTiles[elem.y][elem.x], aim);
                    if (ants.getIlk(newTile).isPassable() && (visibleMap[newTile.getRow()][newTile.getCol()] || includeInvisibleTiles)) {
                        int curDist = distMap[newTile.getRow()][newTile.getCol()];
                        if (elem.count + 1 < curDist) {
                            PathElem newElem = new PathElem(newTile.getCol(), newTile.getRow(), elem.count + 1);
                            pathQueue[queueCount++] = newElem;
                            distMap[newElem.y][newElem.x] = newElem.count;
                            destMap[newElem.y][newElem.x] = destMap[elem.y][elem.x];
                            infoMap[newElem.y][newElem.x] = infoMap[elem.y][elem.x];
                            if (!visibleMap[newElem.y][newElem.x]) {
                                isFullyExplored = false;
                            }
                        }
                    }
                }
            }
        }

        public void logPathMap() {
            String s = "PathMap in turn " + turn + ":\n";
            Set<Tile> foodTiles = ants.getFoodTiles();
            for (int i = 0; i < ants.getRows(); i++) {
                for (int j = 0; j < ants.getCols(); j++) {
                    int o = distMap[i][j];
                    if (o == Integer.MAX_VALUE) {
                        s += "  ";
                    } else if (o < 10) {
                        s += "0" + o;
                    } else {
                        s += o;
                    }
                    boolean f = false;
                    for (Tile t : foodTiles) {
                        if (i == t.getRow() && j == t.getCol()) {
                            s += "@";
                            f = true;
                        }
                    }
                    if (!f) {
                        s += " ";
                    }
                }
                s += "\n";
            }
            logger.info(s);
        }
    }

    private class PathElem {
        public int x, y;
        public int count;
        public PathElem(int x, int y, int count) {
            this.x = x;
            this.y = y;
            this.count = count;
        }
    }

    private class MapElem {
        public Tile tile;
        public Set<MyAnt> ants = new HashSet<MyAnt>();
        public MapElem(Tile tile) {
            this.tile = tile;
        }
    }

    private class MyAnt extends MapElem {
        public Aim order;
        public PathMap destPathMap;
        public MyAnt(Tile tile) {
            super(tile);
        }
    }
}
