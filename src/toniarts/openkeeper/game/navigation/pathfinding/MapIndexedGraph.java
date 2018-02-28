/*
 * Copyright (C) 2014-2016 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.game.navigation.pathfinding;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.DefaultConnection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.utils.Array;
import toniarts.openkeeper.common.RoomInstance;
import toniarts.openkeeper.game.controller.IGameWorldController;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.controller.room.IRoomController;
import toniarts.openkeeper.game.map.MapTile;
import static toniarts.openkeeper.game.navigation.pathfinding.INavigable.DEFAULT_COST;
import static toniarts.openkeeper.game.navigation.pathfinding.INavigable.WATER_COST;
import toniarts.openkeeper.tools.convert.map.Terrain;

/**
 * Map representation for the path finding
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class MapIndexedGraph implements IndexedGraph<MapTile> {

    private final IMapController mapController;
    private final IGameWorldController gameWorldController;
    private final int nodeCount;
    private INavigable pathFindable;

    public MapIndexedGraph(IGameWorldController gameWorldController, IMapController mapController) {
        this.mapController = mapController;
        this.gameWorldController = gameWorldController;
        nodeCount = mapController.getMapData().getHeight() * mapController.getMapData().getWidth();
    }

    @Override
    public int getNodeCount() {
        return nodeCount;
    }

    @Override
    public int getIndex(MapTile n) {
        return n.getIndex();
    }

    /**
     * Set this prior to finding the path to search the path for certain path
     * findable type
     *
     * @param pathFindable the path findable
     */
    public void setPathFindable(INavigable pathFindable) {
        this.pathFindable = pathFindable;
    }

    @Override
    public Array<Connection<MapTile>> getConnections(MapTile tile) {

        // The connections depend on the creature type
        Array<Connection<MapTile>> connections = new Array<>(pathFindable.canMoveDiagonally() ? 8 : 4);
        boolean valids[] = new boolean[4];

        valids[0] = addIfValidCoordinate(tile, tile.getX(), tile.getY() - 1, connections); // North
        valids[1] = addIfValidCoordinate(tile, tile.getX() + 1, tile.getY(), connections); // East
        valids[2] = addIfValidCoordinate(tile, tile.getX(), tile.getY() + 1, connections); // South
        valids[3] = addIfValidCoordinate(tile, tile.getX() - 1, tile.getY(), connections); // West

        if (pathFindable.canMoveDiagonally()) {
            if (valids[0] && valids[1]) { // North-East
                addIfValidCoordinate(tile, tile.getX() + 1, tile.getY() - 1, connections);
            }
            if (valids[0] && valids[3]) { // North-West
                addIfValidCoordinate(tile, tile.getX() - 1, tile.getY() - 1, connections);
            }
            if (valids[2] && valids[1]) { // South-East
                addIfValidCoordinate(tile, tile.getX() + 1, tile.getY() + 1, connections);
            }
            if (valids[2] && valids[3]) { // South-West
                addIfValidCoordinate(tile, tile.getX() - 1, tile.getY() + 1, connections);
            }
        }

        return connections;
    }

    private boolean addIfValidCoordinate(final MapTile startTile, final int x, final int y, final Array<Connection<MapTile>> connections) {

        // Valid coordinate
        MapTile tile = mapController.getMapData().getTile(x, y);
        if (tile != null) {
            Float cost = getCost(pathFindable, startTile, tile, gameWorldController, mapController, false);
            if (cost != null) {
                connections.add(new DefaultConnection<MapTile>(startTile, tile) {

                    @Override
                    public float getCost() {
                        return cost;
                    }

                });
                return true;
            }
        }
        return false;
    }

    /**
     * Can the entity travel from A to B?
     *
     * @param navigable the navigable entity
     * @param from the tile we are traversing from, always the adjacent tile
     * which we know already being accessible
     * @param to the tile we are travelling to
     * @param gameWorldController the game world controller
     * @param mapController the map controller
     * @see #DEFAULT_COST
     * @see #WATER_COST
     * @return {@code null} if the to tile is not accessible
     */
    protected static Float getCost(final INavigable navigable, final MapTile from, final MapTile to, final IGameWorldController gameWorldController, final IMapController mapController) {
        return getCost(navigable, from, to, gameWorldController, mapController, true);
    }

    private static Float getCost(final INavigable navigable, final MapTile from, final MapTile to, final IGameWorldController gameWorldController, final IMapController mapController, boolean checkDiagonal) {
        Terrain terrain = mapController.getTerrain(to);
        if (!terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)) {

            // Check for doors etc.
//            DoorControl doorControl = worldState.getThingLoader().getDoor(to.getLocation());
//            if (doorControl != null && !doorControl.isPassable(getOwnerId())) {
//                return null;
//            }
            // We can never squeeze through obstacles, even if able to move diagonally
            if (checkDiagonal && from != null && from.getX() != to.getX() && from.getY() != to.getY()) {
                if (!navigable.canMoveDiagonally()) {
                    return null;
                }

                // Get the 2 neighbouring tiles (corners kinda)
                boolean hasConnection = false;
                MapTile hiCorner = mapController.getMapData().getTile(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()));
                MapTile loCorner = mapController.getMapData().getTile(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()));
                if (hiCorner != null && getCost(navigable, from, hiCorner, gameWorldController, mapController, false) != null) {
                    hasConnection = true;
                } else if (loCorner != null && getCost(navigable, from, loCorner, gameWorldController, mapController, false) != null) {
                    hasConnection = true;
                }

                if (!hasConnection) {
                    return null;
                }
            }

            // Check terrain
            if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {

                // Get room obstacles
                RoomInstance roomInstance = mapController.getRoomInstanceByCoordinates(to.getLocation());
                IRoomController room = mapController.getRoomController(roomInstance);
                return room.isTileAccessible(from != null ? from.getLocation() : null, to.getLocation()) ? DEFAULT_COST : null;
            } else if (navigable.canFly()) {
                return DEFAULT_COST;
            } else if (terrain.getFlags().contains(Terrain.TerrainFlag.LAVA) && !navigable.canWalkOnLava()) {
                return null;
            } else if (terrain.getFlags().contains(Terrain.TerrainFlag.WATER)) {
                if (navigable.canWalkOnWater()) {
                    return WATER_COST;
                }
                return null;
            }
            return DEFAULT_COST;
        }
        return null;
    }

}
