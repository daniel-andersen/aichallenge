import java.io.IOException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MyBot extends Bot {
    private static final Logger logger = Logger.getLogger(MyBot.class.getName());

    private static final int VISIBLE_TILES_TO_EXPLORE_MAX = 256;
    private static final int BORDER_TILES_TO_EXPLORE_MAX = 256;

    private int turn = 0;
    private Ants ants;
    private Random random;

    private Set<MyAnt> myAnts;
    private Set<MapElem> myFood;
    private Set<MapElem> myFoodUncovered;
    private Set<MapElem> myHills;
    private Set<MapElem> myEnemyAnts;
    private Set<MapElem> myEnemyHills;

    private boolean collisionMap[][];
    private boolean exploredMap[][];
    private boolean visibleMap[][];
    private boolean visibleBorder[][];
    private int enemyAttackMap[][];
    private MyAnt myAntMap[][];

    private boolean raidMode = false;

    private PathMap[] pathMapFood = new PathMap[2];
    private PathMap pathMapMyHills;
    private PathMap pathMapEnemies;
    private PathMap pathMapEnemyHills;
    private PathMap[] pathMapLandToExploreVisibleArea = new PathMap[2];
    private PathMap[] pathMapLandToExploreBorderArea = new PathMap[2];

    private Tile[][] mapTiles;

    private Set<MapElem> visibleTilesToExplore;
    private Set<MapElem> borderTilesToExplore;
    private Set<MapElem> borderTilesToExploreUncovered;

    public static void main(String[] args) throws IOException {
        FileHandler fh = new FileHandler("outputLog.log");
        fh.setFormatter(new SimpleFormatter());
        logger.addHandler(fh);
        new MyBot().readSystemInput();
    }

    public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2, int attackRadius2, int spawnRadius2) {
        super.setup(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);
        random = new Random(System.currentTimeMillis());
        ants = getAnts();
        myAnts = new HashSet<MyAnt>();
        initializePathAlgorithms();
        collisionMap = new boolean[ants.getRows()][ants.getCols()];
        exploredMap = new boolean[ants.getRows()][ants.getCols()];
        visibleMap = new boolean[ants.getRows()][ants.getCols()];
        visibleBorder = new boolean[ants.getRows()][ants.getCols()];
        enemyAttackMap = new int[ants.getRows()][ants.getCols()];
        myAntMap = new MyAnt[ants.getRows()][ants.getCols()];
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(exploredMap[i], false);
            Arrays.fill(visibleMap[i], false);
            Arrays.fill(visibleBorder[i], false);
        }
        visibleTilesToExplore = new HashSet<MapElem>();
        borderTilesToExplore = new HashSet<MapElem>();
    }

    @Override
    public void doTurn() {
        ants = getAnts();
        getObjectsOnMap();
        updateRaidMode();
        updateAttackMaps();
        updateVisibility();
        updateLandToExplore();
        calculatePahtMaps();
        delegateAnts();
        razeEnemy();
        defendOwnHills();
        updateBattlingAnts();
        stepAsideAwaitingAnts();
        issueCommands();
        turn++;
    }

    private void updateAttackMaps() {
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(enemyAttackMap[i], 0);
        }
        for (MapElem enemy : myEnemyAnts) {
            for (int i = 0; i < ATTACK_MAP_ONE_MOVEMENT.length; i++) {
                for (int j = 0; j < ATTACK_MAP_ONE_MOVEMENT.length; j++) {
                    if (ATTACK_MAP_ONE_MOVEMENT[i][j] == 1) {
                        int x = (enemy.tile.getCol() + j - (ATTACK_MAP_ONE_MOVEMENT.length / 2) + ants.getCols()) % ants.getCols();
                        int y = (enemy.tile.getRow() + i - (ATTACK_MAP_ONE_MOVEMENT.length / 2) + ants.getRows()) % ants.getRows();
                        enemyAttackMap[y][x]++;
                    }
                }
            }
        }
    }

    private void updateRaidMode() {
        if (myAnts.size() > 50 && myAnts.size() > myEnemyAnts.size() * 8 / 4) {
            raidMode = true;
        }
        if (myAnts.size() < 10 || myAnts.size() < myEnemyAnts.size() * 6 / 4) {
            raidMode = false;
        }
    }

    private void updateVisibility() {
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (ants.isVisible(mapTiles[i][j])) {
                    if (visibleBorder[i][j] || !visibleMap[i][j]) {
                        visibleBorder[i][j] = false;
                        for (Aim aim : Aim.values()) {
                            int col = (j + aim.getColDelta() + ants.getCols()) % ants.getCols();
                            int row = (i + aim.getRowDelta() + ants.getRows()) % ants.getRows();
                            if (!visibleMap[row][col]) {
                                visibleBorder[row][col] = true;
                            }
                        }
                    }
                    visibleMap[i][j] = true;
                }
            }
        }
    }

    private void updateLandToExplore() {
        int visibleBorderCount = 0;
        int visibleMapCount = 0;
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (visibleMap[i][j] && ants.getIlk(mapTiles[i][j]).isPassable()) {
                    visibleMapCount++;
                }
                if (visibleBorder[i][j] && ants.getIlk(mapTiles[i][j]).isPassable()) {
                    visibleBorderCount++;
                }
            }
        }
        for (MyAnt ant : myAnts) {
            removeTileIfAlreadyExplored(pathMapLandToExploreBorderArea[0], ant, borderTilesToExplore);
            removeTileIfAlreadyExplored(pathMapLandToExploreBorderArea[1], ant, borderTilesToExplore);
            removeTileIfAlreadyExplored(pathMapLandToExploreVisibleArea[0], ant, visibleTilesToExplore);
            removeTileIfAlreadyExplored(pathMapLandToExploreVisibleArea[1], ant, visibleTilesToExplore);
        }
        int visibileCount = Math.max(Math.min(visibleMapCount / 10, VISIBLE_TILES_TO_EXPLORE_MAX), 1);
        int borderCount = Math.max(Math.min(visibleBorderCount / 10, BORDER_TILES_TO_EXPLORE_MAX), 64);
        for (int i = visibleTilesToExplore.size(); i < visibileCount; i++) {
            addRandomVisibleTileToExplore(visibleMapCount);
        }
        if (visibleTilesToExplore.size() < VISIBLE_TILES_TO_EXPLORE_MAX) {
            addRandomVisibleTileToExplore(visibleMapCount);
        }
        for (int i = borderTilesToExplore.size(); i < borderCount; i++) {
            addRandomBorderTileToExplore(visibleBorderCount);
        }
        if (borderTilesToExplore.size() < BORDER_TILES_TO_EXPLORE_MAX) {
            addRandomBorderTileToExplore(visibleBorderCount);
        }
    }

    private void removeTileIfAlreadyExplored(PathMap pathMap, MyAnt ant, Set<MapElem> tilesToExplore) {
        int dist = pathMap.distInPathMap(ant.tile);
        if (dist >= 5) {
            return;
        }
        MapElem elemToExplore = pathMap.infoInPathMap(ant.tile);
        if (elemToExplore == null) {
            return;
        }
        tilesToExplore.remove(elemToExplore);
    }

    private void addRandomVisibleTileToExplore(int visibleMapCount) {
        int c = (int) (random.nextFloat() * (float) visibleMapCount);
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (visibleMap[i][j] && ants.getIlk(mapTiles[i][j]).isPassable()) {
                    if (c-- <= 0) {
                        visibleTilesToExplore.add(new MapElem(mapTiles[i][j]));
                        return;
                    }
                }
            }
        }
    }

    private void addRandomBorderTileToExplore(int visibleBorderCount) {
        int c = (int) (random.nextFloat() * (float) visibleBorderCount);
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (visibleBorder[i][j] && ants.getIlk(mapTiles[i][j]).isPassable()) {
                    if (c-- <= 0) {
                        borderTilesToExplore.add(new MapElem(mapTiles[i][j]));
                        return;
                    }
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
        pathMapLandToExploreBorderArea[0].calculateMap(borderTilesToExplore, false);
        borderTilesToExploreUncovered = getUncoveredElems(pathMapLandToExploreBorderArea[0], borderTilesToExplore);
        pathMapLandToExploreBorderArea[1].calculateMap(getUncoveredElems(pathMapLandToExploreBorderArea[0], borderTilesToExplore), false);
        pathMapLandToExploreVisibleArea[0].calculateMap(visibleTilesToExplore, false);
        pathMapLandToExploreVisibleArea[1].calculateMap(getUncoveredElems(pathMapLandToExploreVisibleArea[0], visibleTilesToExplore), false);
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
            exploredMap[ant.tile.getRow()][ant.tile.getCol()] = true;
            if (ant.order == null) {
                collisionMap[ant.tile.getRow()][ant.tile.getCol()] = true;
            }
        }
        for (MyAnt ant : myAnts) {
            if (ant.order == null) {
                continue;
            }
            Tile dest = ants.getTile(ant.tile, ant.order);
            if (!collisionMap[dest.getRow()][dest.getCol()] && ants.getIlk(dest).isPassable()) {
                collisionMap[dest.getRow()][dest.getCol()] = true;
                ants.issueOrder(ant.tile, ant.order);
            } else {
                moveToAvoidCollision(ant);
            }
        }
    }

    private void moveToAvoidCollision(MyAnt ant) {
        for (Aim aim : PREFERRED_MOVE_DIRECTION[ant.order.ordinal()]) {
            Tile dest = ants.getTile(ant.tile, aim);
            if (!collisionMap[dest.getRow()][dest.getCol()] && ants.getIlk(dest).isPassable()) {
                collisionMap[dest.getRow()][dest.getCol()] = true;
                ant.order = aim;
                ants.issueOrder(ant.tile, ant.order);
                return;
            }
        }
        collisionMap[ant.tile.getRow()][ant.tile.getCol()] = true;
        ant.order = null;
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
                    ant.isDefendingBase = true;
                }
            }
            if (defenders.size() >= numDefendersPerHill) {
                continue;
            }
            MyAnt nearestAnt = null;
            int nearestDist = Integer.MAX_VALUE;
            for (MyAnt ant : myAnts) {
                if (ants.getDistance(hill.tile, ant.tile) < ants.getAttackRadius2() * 2) {
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
                nearestAnt.isDefendingBase = true;
            }
        }
    }

    private void razeEnemy() {
        if (!raidMode) {
            return;
        }
        for (MyAnt ant : myAnts) {
            if (ant.isDefendingBase) {
                continue;
            }
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

    private MyAnt findClosestAnt(PathMap pathMap, Set<MyAnt> antSet) {
        int minDist = Integer.MAX_VALUE;
        MyAnt minAnt = null;
        for (MyAnt ant : antSet) {
            int dist = pathMap.distInPathMap(ant.tile);
            if (dist < minDist) {
                minDist = dist;
                minAnt = ant;
            }
        }
        return minAnt;
    }

    private void updateBattlingAnts() {
        for (MyAnt ant : myAnts) {
            MapElem enemy = pathMapEnemies.infoInPathMap(ant.tile);
            if (enemy != null) {
                enemy.ants.add(ant);
            }
        }
        for (MyAnt ant : myAnts) {
            for (Aim aim : Aim.values()) {
                Tile tile = ants.getTile(ant.tile, aim);
                if (enemyAttackMap[tile.getRow()][tile.getCol()] != 0) {
                    ant.fightingDirection = aim;
                    break;
                }
            }
        }
        for (MapElem enemy : myEnemyAnts) {
            for (MyAnt ant : enemy.ants) {
                if (ant.fightingDirection != null) {
                    updateBattlingAnt(ant);
                }
            }
        }
    }

    private void stepAsideAwaitingAnts() {
        for (MyAnt ant : myAnts) {
            if (ant.order != null) {
                continue;
            }
            Aim stepAside = null;
            for (Aim aim : Aim.values()) {
                if (ants.getIlk(ant.tile, aim) != Ilk.MY_ANT) {
                    continue;
                }
                Tile tile = ants.getTile(ant.tile, aim);
                MyAnt otherAnt = myAntMap[tile.getRow()][tile.getCol()];
                if (otherAnt.order == null) {
                    continue;
                }
                if (ant.tile.equals(ants.getTile(otherAnt.tile, otherAnt.order))) {
                    stepAside = otherAnt.order;
                }
            }
            if (stepAside == null) {
                continue;
            }
            for (Aim aim : PREFERRED_STEP_ASIDE_DIRECTION[stepAside.ordinal()]) {
                Tile stepAsideTile = ants.getTile(ant.tile, aim);
                if (!ants.getIlk(stepAsideTile).isPassable()) {
                    continue;
                }
                if (enemyAttackMap[stepAsideTile.getRow()][stepAsideTile.getCol()] != 0) {
                    continue;
                }
                ant.destPathMap = pathMapEnemies;
                ant.order = aim;
                break;
            }
        }
    }

    private Set<Tile> antsInAttackRange(Tile tile, Ilk ilk, int[][] attackMap) {
        Set<Tile> antsInRange = new HashSet<Tile>();
        for (int i = 0; i < attackMap.length; i++) {
            for (int j = 0; j < attackMap.length; j++) {
                if (attackMap[i][j] == 0) {
                    continue;
                }
                int mx = (tile.getCol() + j - (attackMap.length / 2) + ants.getCols()) % ants.getCols();
                int my = (tile.getRow() + i - (attackMap.length / 2) + ants.getRows()) % ants.getRows();
                Tile attackTile = mapTiles[my][mx];
                if (ants.getIlk(attackTile) == ilk) {
                    antsInRange.add(attackTile);
                }
            }
        }
        return antsInRange;
    }

    private void updateBattlingAnt(MyAnt ant) {
        if (ant.isDefendingBase || ant.isFighting) {
            return;
        }
        Tile attackinTile = ants.getTile(ant.tile, ant.fightingDirection);
        Set<Tile> enemiesInRangeOfMyAnt = antsInAttackRange(attackinTile, Ilk.ENEMY_ANT, ATTACK_MAP_TWO_MOVEMENTS);
        for (Tile enemyTile : enemiesInRangeOfMyAnt) {
            Set<Tile> myAntsInRangeOfEnemy = antsInAttackRange(enemyTile, Ilk.MY_ANT, ATTACK_MAP_TWO_MOVEMENTS);
            if (enemiesInRangeOfMyAnt.size() >= myAntsInRangeOfEnemy.size()) {
                continue;
            }
            int movesBlocked = getBlockedMovesCount(myAntsInRangeOfEnemy, enemyTile);
            if (enemiesInRangeOfMyAnt.size() >= myAntsInRangeOfEnemy.size() - movesBlocked) {
                continue;
            }
            for (Tile antTile : myAntsInRangeOfEnemy) {
                MyAnt otherAnt = myAntMap[antTile.getRow()][antTile.getCol()];
                otherAnt.destPathMap = pathMapEnemies;
                List<Aim> directions = ants.getDirections(otherAnt.tile, enemyTile);
                otherAnt.order = getClosestDirectionToEnemy(enemyTile, otherAnt.tile, directions);
                otherAnt.isFighting = true;
            }
            return;
        }
        if (enemiesInRangeOfMyAnt.size() == 1) {
            for (Tile enemy : enemiesInRangeOfMyAnt) {
                if (pathMapFood[0].distInPathMap(enemy) < pathMapFood[0].distInPathMap(ant.tile)) {
                    ant.destPathMap = pathMapEnemies;
                    ant.order = pathMapEnemies.getFirstMovementInPath(ant.tile);
                    return;
                }
            }
        }
        ant.destPathMap = pathMapEnemies;
        ant.order = enemyAttackMap[ant.tile.getRow()][ant.tile.getCol()] != 0 ? getGoodMoveAwayFromEnemy(ant) : null;
    }

    private int getBlockedMovesCount(Set<Tile> antSet, Tile dest) {
        int movesBlocked = 0;
        for (Tile antTile : antSet) {
            List<Aim> aim = ants.getDirections(antTile, dest);
            if (ants.getIlk(antTile, aim.get(0)).isPassable()) {
                continue;
            }
            if (aim.size() > 1 && ants.getIlk(antTile, aim.get(1)).isPassable()) {
                continue;
            }
            movesBlocked++;
        }
        return movesBlocked;
    }

    private Aim getClosestDirectionToEnemy(Tile enemyTile, Tile antTile, List<Aim> directions) {
        if (directions.size() == 1) {
            return directions.get(0);
        }
        int dist1 = ants.getDistance(enemyTile, ants.getTile(antTile, directions.get(0)));
        int dist2 = ants.getDistance(enemyTile, ants.getTile(antTile, directions.get(1)));
        return dist1 < dist2 ? directions.get(0) : directions.get(1);
    }

    private Aim getGoodMoveAwayFromEnemy(MyAnt ant) {
        Set<Aim> aims = new HashSet<Aim>();
        if (ant.order != null) {
            aims.add(ant.order);
        }
        PathMap[] pathMaps = new PathMap[] {pathMapFood[0], pathMapFood[1], pathMapEnemyHills, pathMapLandToExploreBorderArea[0], pathMapLandToExploreBorderArea[1], pathMapLandToExploreVisibleArea[0], pathMapLandToExploreVisibleArea[1], pathMapMyHills};
        for (PathMap pathMap : pathMaps) {
            if (pathMap.destInPathMap(ant.tile) != null) {
                Aim aim = pathMap.getFirstMovementInPath(ant.tile);
                if (aim != null) {
                    aims.add(aim);
                }
            }
        }
        aims.add(Aim.NORTH);
        aims.add(Aim.EAST);
        aims.add(Aim.SOUTH);
        aims.add(Aim.WEST);
        for (Aim aim : aims) {
            Tile tile = ants.getTile(ant.tile, aim);
            if (enemyAttackMap[tile.getRow()][tile.getCol()] == 0) {
                return aim;
            }
        }
        return null;
    }

    private void delegateAnts() {
        delegateAntsOnPathMap(pathMapFood[0], 32);
        restrictNumberOfAntsPerDestination(pathMapFood[0], myFood, 1);
        delegateAntsOnPathMap(pathMapFood[1], 32);
        restrictNumberOfAntsPerDestination(pathMapFood[1], myFoodUncovered, 1);
        delegateAntsOnPathMap(pathMapEnemies, 12);
        restrictNumberOfAntsPerDestination(pathMapEnemies, myEnemyAnts, 6);
        delegateAntsOnPathMap(pathMapEnemyHills, 16);
        restrictNumberOfAntsPerDestination(pathMapEnemyHills, myEnemyHills, 8);
        delegateAntsOnPathMap(pathMapLandToExploreBorderArea[0], 32);
        restrictNumberOfAntsPerDestination(pathMapLandToExploreBorderArea[0], borderTilesToExplore, 1);
        delegateAntsOnPathMap(pathMapLandToExploreBorderArea[1], 32);
        restrictNumberOfAntsPerDestination(pathMapLandToExploreBorderArea[1], borderTilesToExploreUncovered, 1);
        delegateAntsOnPathMap(pathMapLandToExploreVisibleArea[0], Integer.MAX_VALUE);
        splitAntsOnPathMap(pathMapLandToExploreVisibleArea, visibleTilesToExplore);
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
                MyAnt closestAnt = findClosestAnt(pathMap, antsOrig);
                antsLeft.add(closestAnt);
                antsOrig.remove(closestAnt);
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
        for (int i = 0; i < ants.getRows(); i++) {
            Arrays.fill(myAntMap[i], null);
        }
        myAnts = new HashSet<MyAnt>();
        for (Tile ant : ants.getMyAnts()) {
            MyAnt myAnt = new MyAnt(ant);
            myAnts.add(myAnt);
            myAntMap[ant.getRow()][ant.getCol()] = myAnt;
        }
        myEnemyAnts = new HashSet<MapElem>();
        for (Tile enemy : ants.getEnemyAnts()) {
            MapElem enemyAnt = new MapElem(enemy);
            myEnemyAnts.add(enemyAnt);
        }
        myFood = new HashSet<MapElem>();
        for (Tile food : ants.getFoodTiles()) {
            myFood.add(new MapElem(food));
        }
        myHills = new HashSet<MapElem>();
        for (Tile myHill : ants.getMyHills()) {
            myHills.add(new MapElem(myHill));
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
                Ilk ilk = ants.getIlk(mapTiles[i][j]);
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

    private void logVisibleMap() {
        char s[][] = new char[ants.getRows()][ants.getCols()];
        String r = "Map in turn " + turn + ":\n";
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                if (ants.getIlk(new Tile(i, j)) == Ilk.WATER) {
                    s[i][j] = '@';
                } else if (visibleBorder[i][j]) {
                    s[i][j] = '*';
                } else if (visibleMap[i][j]) {
                    s[i][j] = '.';
                } else {
                    s[i][j] = ' ';
                }
            }
        }
        for (MapElem elem : visibleTilesToExplore) {
            s[elem.tile.getRow()][elem.tile.getCol()] = 'V';
        }
        for (MapElem elem : borderTilesToExplore) {
            s[elem.tile.getRow()][elem.tile.getCol()] = 'B';
        }
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                r += s[i][j];
            }
            r += "\n";
        }
        logger.info(r);
    }


    /*
        PATH ALGORITHMS
        ---------------
     */

    private void initializePathAlgorithms() {
        mapTiles = new Tile[ants.getRows()][ants.getCols()];
        for (int i = 0; i < ants.getRows(); i++) {
            for (int j = 0; j < ants.getCols(); j++) {
                mapTiles[i][j] = new Tile(i, j);
            }
        }
        pathMapFood[0] = new PathMap(1024 * 24, true);
        pathMapFood[1] = new PathMap(1024 * 24, true);
        pathMapMyHills = new PathMap(1024 * 1, false);
        pathMapEnemies = new PathMap(1024 * 16, false);
        pathMapEnemyHills = new PathMap(1024 * 8, false);
        pathMapLandToExploreBorderArea[0] = new PathMap(1024 * 8, true);
        pathMapLandToExploreBorderArea[1] = new PathMap(1024 * 8, true);
        pathMapLandToExploreVisibleArea[0] = new PathMap(1024 * 16, true);
        pathMapLandToExploreVisibleArea[1] = new PathMap(1024 * 16, true);
    }

    private class PathMap {
        public int[][] distMap;
        public Tile[][] destMap;
        public MapElem[][] infoMap;
        private PathElem[] pathQueue;
        private int maxQueueLength;
        public boolean isFullyExplored = false;
        public boolean shouldAvoidEnemies = false;

        public PathMap(int maxQueueLength, boolean shouldAvoidEnemies) {
            this.maxQueueLength = maxQueueLength;
            this.shouldAvoidEnemies = shouldAvoidEnemies;
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
                if (dist < minDist && ants.getIlk(tile).isPassable()) {
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
            int queueIndex = 0;
            int queueCount = 0;
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
            isFullyExplored = true;
            while (queueIndex < queueCount && queueCount < maxQueueLength - 4) {
                PathElem elem = pathQueue[queueIndex++];
                for (Aim aim: Aim.values()) {
                    Tile newTile = ants.getTile(mapTiles[elem.y][elem.x], aim);
                    if (!ants.getIlk(newTile).isPassable()) {
                        continue;
                    }
                    if (!visibleMap[newTile.getRow()][newTile.getCol()] && !includeInvisibleTiles) {
                        continue;
                    }
                    if (shouldAvoidEnemies && enemyAttackMap[newTile.getRow()][newTile.getCol()] != 0) {
                        continue;
                    }
                    int curDist = distMap[newTile.getRow()][newTile.getCol()];
                    if (elem.count + 1 >= curDist) {
                        continue;
                    }
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
                    if (ants.getIlk(mapTiles[i][j]) == Ilk.FOOD) {
                        s += "%";
                    } else if (ants.getIlk(mapTiles[i][j]) == Ilk.MY_ANT) {
                        s += "*";
                    } else if (ants.getIlk(mapTiles[i][j]) == Ilk.ENEMY_ANT) {
                        s += "@";
                    } else {
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
        public int appearCountDown = 0;
        public MapElem(Tile tile) {
            this.tile = tile;
        }
    }

    private class MyAnt extends MapElem {
        public Aim order;
        public PathMap destPathMap;
        public Aim fightingDirection = null;
        public boolean isFighting = false;
        public boolean isDefendingBase = false;

        public MyAnt(Tile tile) {
            super(tile);
        }
    }

    private static final int[][] ATTACK_MAP_ONE_MOVEMENT = new int[][]
            {
                    {0, 0, 1, 1, 1, 0, 0},
                    {0, 1, 1, 1, 1, 1, 0},
                    {1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1},
                    {0, 1, 1, 1, 1, 1, 0},
                    {0, 0, 1, 1, 1, 0, 0}
            };
    private static final int[][] ATTACK_MAP_TWO_MOVEMENTS = new int[][]
            {
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {1, 1, 1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1, 1, 1},
                    {1, 1, 1, 1, 1, 1, 1, 1, 1},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0},
                    {0, 0, 1, 1, 1, 1, 1, 0, 0}
            };

    private static final Aim PREFERRED_STEP_ASIDE_DIRECTION[][] =
            {
                    {Aim.EAST, Aim.WEST, Aim.NORTH}, // Other ant from NORTH
                    {Aim.SOUTH, Aim.NORTH, Aim.EAST}, // Other ant from EAST
                    {Aim.WEST, Aim.EAST, Aim.SOUTH}, // Other ant from SOUTH
                    {Aim.NORTH, Aim.SOUTH, Aim.WEST}, // Other ant from WEST
            };

    private static final Aim PREFERRED_MOVE_DIRECTION[][] =
            {
                    {Aim.EAST, Aim.WEST, Aim.SOUTH}, // Obstacle at NORTH
                    {Aim.SOUTH, Aim.NORTH, Aim.WEST}, // Obstacle at EAST
                    {Aim.WEST, Aim.EAST, Aim.NORTH}, // Obstacle at SOUTH
                    {Aim.NORTH, Aim.SOUTH, Aim.EAST}, // Obstacle at WEST
            };

    private static final Aim REVERSE_DIRECTION[] = {Aim.SOUTH, Aim.WEST, Aim.NORTH, Aim.EAST};
}
