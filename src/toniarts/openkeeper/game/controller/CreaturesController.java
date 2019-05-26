/*
 * Copyright (C) 2014-2017 OpenKeeper
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
package toniarts.openkeeper.game.controller;

import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import java.awt.Point;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import toniarts.openkeeper.game.component.CreatureAi;
import toniarts.openkeeper.game.component.CreatureComponent;
import toniarts.openkeeper.game.component.CreatureEfficiency;
import toniarts.openkeeper.game.component.CreatureExperience;
import toniarts.openkeeper.game.component.CreatureImprisoned;
import toniarts.openkeeper.game.component.CreatureMeleeAttack;
import toniarts.openkeeper.game.component.CreatureMood;
import toniarts.openkeeper.game.component.CreatureSleep;
import toniarts.openkeeper.game.component.CreatureTortured;
import toniarts.openkeeper.game.component.CreatureViewState;
import toniarts.openkeeper.game.component.Death;
import toniarts.openkeeper.game.component.Fearless;
import toniarts.openkeeper.game.component.Gold;
import toniarts.openkeeper.game.component.Health;
import toniarts.openkeeper.game.component.Interaction;
import toniarts.openkeeper.game.component.Mobile;
import toniarts.openkeeper.game.component.Objective;
import toniarts.openkeeper.game.component.Owner;
import toniarts.openkeeper.game.component.Party;
import toniarts.openkeeper.game.component.Position;
import toniarts.openkeeper.game.component.Senses;
import toniarts.openkeeper.game.component.Threat;
import toniarts.openkeeper.game.component.Trigger;
import toniarts.openkeeper.game.controller.creature.CreatureController;
import toniarts.openkeeper.game.controller.creature.CreatureState;
import toniarts.openkeeper.game.controller.creature.ICreatureController;
import toniarts.openkeeper.game.controller.creature.IPartyController;
import toniarts.openkeeper.game.controller.creature.PartyController;
import toniarts.openkeeper.game.controller.creature.PartyType;
import toniarts.openkeeper.game.controller.room.AbstractRoomController;
import toniarts.openkeeper.game.controller.room.IRoomController;
import toniarts.openkeeper.tools.convert.map.Creature;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.map.Player;
import toniarts.openkeeper.tools.convert.map.Thing;
import toniarts.openkeeper.tools.convert.map.Variable;
import toniarts.openkeeper.utils.Utils;
import toniarts.openkeeper.utils.WorldUtils;
import toniarts.openkeeper.world.MapLoader;

/**
 * This is a controller that controls all the game objects in the world TODO:
 * Hmm, should this be more a factory/loader maybe, or if this offers the
 * ability to load / save, then it is fine
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class CreaturesController implements ICreaturesController {

    private final KwdFile kwdFile;
    private final EntityData entityData;
    private final Map<Variable.MiscVariable.MiscType, Variable.MiscVariable> gameSettings;
    private final Map<Short, IPartyController> creaturePartiesByPartyId = new HashMap<>();
    /**
     * These are the actual living parties on the map by the generated party ID
     */
    private final Map<Long, IPartyController> creaturePartiesById = new HashMap<>();
    /**
     * These are the map defined parties
     */
    private final Map<Short, Thing.HeroParty> heroParties = new HashMap<>();
    /**
     * I don't know how to design this perfectly in the entity world, we have
     * the state machine running inside an CreatureController. That is probably
     * wrong (should be inside a system instead). But while it is in there, we
     * should share the instances for it to function properly.<br>
     * The value needs to be weak reference also since it references the key
     */
    private final Map<EntityId, WeakReference<ICreatureController>> creatureControllersByEntityId = new WeakHashMap<>();
    private final IGameTimer gameTimer;
    private final IGameController gameController;
    private final IMapController mapController;
    private final ILevelInfo levelInfo;

    private static final Logger LOGGER = Logger.getLogger(CreaturesController.class.getName());

    /**
     * Load creatures from a KWD file straight (new game)
     *
     * @param kwdFile the KWD file
     * @param entityData the entity controller
     * @param gameSettings the game settings
     * @param gameTimer
     * @param gameController
     * @param mapController
     */
    public CreaturesController(KwdFile kwdFile, EntityData entityData, Map<Variable.MiscVariable.MiscType, Variable.MiscVariable> gameSettings, IGameTimer gameTimer,
            IGameController gameController, IMapController mapController, ILevelInfo levelInfo) {
        this.kwdFile = kwdFile;
        this.entityData = entityData;
        this.gameSettings = gameSettings;
        this.gameTimer = gameTimer;
        this.gameController = gameController;
        this.mapController = mapController;
        this.levelInfo = levelInfo;

        // Load creatures
        loadCreatures();
    }

    private void loadCreatures() {
        for (Thing.GoodCreature creature : kwdFile.getThings(Thing.GoodCreature.class)) {
            try {
                spawnCreature(creature, new Vector2f(creature.getPosX(), creature.getPosY()));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not load Thing " + creature + "!", ex);
            }
        }
        for (Thing.NeutralCreature creature : kwdFile.getThings(Thing.NeutralCreature.class)) {
            try {
                spawnCreature(creature, new Vector2f(creature.getPosX(), creature.getPosY()));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not load Thing " + creature + "!", ex);
            }
        }
        for (Thing.KeeperCreature creature : kwdFile.getThings(Thing.KeeperCreature.class)) {
            try {
                spawnCreature(creature, new Vector2f(creature.getPosX(), creature.getPosY()));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not load Thing " + creature + "!", ex);
            }
        }
        for (Thing.DeadBody creature : kwdFile.getThings(Thing.DeadBody.class)) {
            try {
                spawnCreature(creature, new Vector2f(creature.getPosX(), creature.getPosY()));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not load Thing " + creature + "!", ex);
            }
        }
        for (Thing.HeroParty heroParty : kwdFile.getThings(Thing.HeroParty.class)) {
            try {
                heroParties.put(heroParty.getId(), heroParty);
                creaturePartiesByPartyId.put(heroParty.getId(), new PartyController(heroParty));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Could not load Thing " + heroParty + "!", ex);
            }
        }
    }

    @Override
    public EntityId spawnCreature(Thing.Creature creature, Vector2f position) {
        Integer triggerId = null;
        short ownerId = 0;
        Integer healthPercentage = null;
        short level = 1;
        Thing.HeroParty.Objective objective = null;
        short objectiveTargetPlayerId = 0;
        int objectiveTargetActionPointId = 0;
        if (creature instanceof Thing.GoodCreature) {
            Thing.GoodCreature goodCreature = (Thing.GoodCreature) creature;
            triggerId = goodCreature.getTriggerId();
            healthPercentage = goodCreature.getInitialHealth();
            level = goodCreature.getLevel();
            ownerId = Player.GOOD_PLAYER_ID;
            objective = goodCreature.getObjective();
            objectiveTargetPlayerId = goodCreature.getObjectiveTargetPlayerId();
            objectiveTargetActionPointId = goodCreature.getObjectiveTargetActionPointId();
        } else if (creature instanceof Thing.NeutralCreature) {
            Thing.NeutralCreature neutralCreature = (Thing.NeutralCreature) creature;
            triggerId = neutralCreature.getTriggerId();
            healthPercentage = neutralCreature.getInitialHealth();
            level = neutralCreature.getLevel();
            ownerId = Player.NEUTRAL_PLAYER_ID;
        } else if (creature instanceof Thing.KeeperCreature) {
            Thing.KeeperCreature keeperCreature = (Thing.KeeperCreature) creature;
            triggerId = keeperCreature.getTriggerId();
            healthPercentage = keeperCreature.getInitialHealth();
            level = keeperCreature.getLevel();
            ownerId = keeperCreature.getPlayerId();
        } else if (creature instanceof Thing.DeadBody) {
            Thing.DeadBody deadBody = (Thing.DeadBody) creature;
            ownerId = deadBody.getPlayerId();
        }
        return loadCreature(creature.getCreatureId(), ownerId, level, position.getX(), position.getY(), 0f, healthPercentage, creature.getGoldHeld(),
                triggerId != null && triggerId != 0 ? triggerId : null, false, objective, objectiveTargetPlayerId, objectiveTargetActionPointId);
    }

    @Override
    public EntityId spawnCreature(short creatureId, short playerId, int level, Vector2f position, boolean entrance) {
        return loadCreature(creatureId, playerId, level, position.x, position.y, 0, 100, 0, null, entrance, null, (short) 0, 0);
    }

    private EntityId loadCreature(short creatureId, short ownerId, int level, float x, float y, float rotation, Integer healthPercentage, int money,
            Integer triggerId, boolean entrance, Thing.HeroParty.Objective objective, short objectiveTargetPlayerId, int objectiveTargetActionPointId) {
        Creature creature = kwdFile.getCreature(creatureId);
        EntityId entity = entityData.createEntity();

        // Create health, unless dead body
        Health healthComponent = healthPercentage != null ? new Health(0, healthPercentage, 100, false) : null;

        Gold goldComponent = new Gold(money, 0);
        Senses sensesComponent = healthComponent != null ? new Senses(creature.getAttributes().getDistanceCanHear(), creature.getAttributes().getDistanceCanSee()) : null;
        CreatureMeleeAttack creatureMeleeAttack = new CreatureMeleeAttack(creature.getMeleeAttackType().getValue(), creature.getMeleeDamage(), creature.getMeleeRecharge(), creature.getMeleeRange());

        // The creature itself
        CreatureComponent creatureComponent = new CreatureComponent();
        creatureComponent.name = Utils.generateCreatureName();
        creatureComponent.bloodType = Utils.generateBloodType();
        creatureComponent.creatureId = creatureId;
        creatureComponent.worker = creature.getFlags().contains(Creature.CreatureFlag.IS_WORKER);
        creatureComponent.stunDuration = creature.getAttributes().getStunDuration();

        // The creature experience
        CreatureExperience creatureExperience = new CreatureExperience();
        creatureExperience.level = level;

        // Threat
        Threat threatComponent = new Threat();

        // Fearless
        if (creature.getFlags().contains(Creature.CreatureFlag.IS_FEARLESS)) {
            entityData.setComponent(entity, new Fearless(null));
        }

        // Need for sleep
        if (creature.getAttributes().getTimeSleep() > 0) {
            entityData.setComponent(entity, new CreatureSleep(null, gameTimer.getGameTime(), 0));
        }

        CreatureState creatureState = entrance ? CreatureState.ENTERING_DUNGEON : getCreatureStateByMapLocation(WorldUtils.vectorToPoint(x, y), ownerId, entity);
        entityData.setComponent(entity, new CreatureAi(gameTimer.getGameTime(), creatureState, creatureId));

        // Set every attribute by the level of the created creature
        setAttributesByLevel(creatureComponent, creatureExperience, healthComponent, goldComponent, sensesComponent, threatComponent, creatureMeleeAttack);

        entityData.setComponent(entity, creatureComponent);
        entityData.setComponent(entity, creatureExperience);
        if (healthComponent != null) {
            entityData.setComponent(entity, healthComponent);
        } else {
            entityData.setComponent(entity, new Death(gameTimer.getGameTime()));
        }
        if (sensesComponent != null) {
            entityData.setComponent(entity, sensesComponent);
        }
        entityData.setComponent(entity, new Owner(ownerId));
        entityData.setComponent(entity, goldComponent);
        entityData.setComponent(entity, threatComponent);

        // Melee attack
        entityData.setComponent(entity, creatureMeleeAttack);

        // I guess the initial efficiency is 80%
        entityData.setComponent(entity, new CreatureEfficiency(80));

        // I guess the initial mood is 10000
        entityData.setComponent(entity, new CreatureMood(10000));

        // Position
        // FIXME: no floor height
        entityData.setComponent(entity, new Position(rotation, new Vector3f(x, MapLoader.FLOOR_HEIGHT, y)));

        // Mobility
        entityData.setComponent(entity, new Mobile(creature.getFlags().contains(Creature.CreatureFlag.CAN_FLY),
                creature.getFlags().contains(Creature.CreatureFlag.CAN_WALK_ON_WATER),
                creature.getFlags().contains(Creature.CreatureFlag.CAN_WALK_ON_LAVA), creatureComponent.speed));

        // Objective
        if (objective != null) {
            entityData.setComponent(entity, new Objective(objective, objectiveTargetPlayerId, objectiveTargetActionPointId));
        }

        // Trigger
        if (triggerId != null) {
            entityData.setComponent(entity, new Trigger(triggerId));
        }

        // Add some interaction properties
        if (creature.getFlags().contains(Creature.CreatureFlag.CAN_BE_SLAPPED) || creature.getFlags().contains(Creature.CreatureFlag.CAN_BE_PICKED_UP)) {
            entityData.setComponent(entity, new Interaction(true, creature.getFlags().contains(Creature.CreatureFlag.CAN_BE_SLAPPED), creature.getFlags().contains(Creature.CreatureFlag.CAN_BE_PICKED_UP), false));
        }

        // Visuals
        Creature.AnimationType animationType = getStartingAnimation(healthComponent, creatureState);
        entityData.setComponent(entity, new CreatureViewState(creatureId, gameTimer.getGameTime(), animationType));

        return entity;
    }

    private Creature.AnimationType getStartingAnimation(Health healthComponent, CreatureState creatureState) {
        if (healthComponent == null) {
            return Creature.AnimationType.DEATH_POSE;
        }
        if (creatureState == CreatureState.ENTERING_DUNGEON) {
            return Creature.AnimationType.ENTRANCE;
        }
        if (creatureState == CreatureState.TORTURED) {
            return Creature.AnimationType.TORTURED_CHAIR;
        }
        if (creatureState == CreatureState.IMPRISONED) {
            return Creature.AnimationType.ANGRY;
        }

        return Creature.AnimationType.IDLE_1;
    }

    private CreatureState getCreatureStateByMapLocation(Point location, short ownerId, EntityId entityId) {
        IRoomController room = mapController.getRoomControllerByCoordinates(location);
        if (room != null && room.getRoomInstance().getOwnerId() != ownerId) {

            // See if tortured or imprisoned
            // TODO: Capacities? Or maybe at this point we are just populating stuff and everything is ok like this?
            if (room.hasObjectControl(AbstractRoomController.ObjectType.PRISONER)) {
                room.getObjectControl(AbstractRoomController.ObjectType.PRISONER).addItem(entityId, location);
                entityData.setComponent(entityId, new CreatureImprisoned(gameTimer.getGameTime(), gameTimer.getGameTime()));
                return CreatureState.IMPRISONED;
            }
            if (room.hasObjectControl(AbstractRoomController.ObjectType.TORTUREE)) {
                room.getObjectControl(AbstractRoomController.ObjectType.TORTUREE).addItem(entityId, location);
                entityData.setComponent(entityId, new CreatureTortured(gameTimer.getGameTime(), gameTimer.getGameTime()));
                return CreatureState.TORTURED;
            }
        }
        return CreatureState.IDLE;
    }

    @Override
    public void levelUpCreatures(short playerId, int level) {

        // Find all the living creatures of the wanted player
        EntitySet entities = entityData.getEntities(new FieldFilter<>(Owner.class, "ownerId", playerId), Owner.class, CreatureComponent.class, Health.class);
        for (Entity entity : entities) {
            levelUpCreature(entity.getId(), level, 0);
        }
    }

    @Override
    public void levelUpCreature(EntityId entityId, int level, int experience) {

        // Get all the components needed
        CreatureComponent creatureComponent = entityData.getComponent(entityId, CreatureComponent.class);
        CreatureExperience creatureExperience = entityData.getComponent(entityId, CreatureExperience.class);
        Health health = entityData.getComponent(entityId, Health.class);
        Gold gold = entityData.getComponent(entityId, Gold.class);
        Senses senses = entityData.getComponent(entityId, Senses.class);
        Threat threat = entityData.getComponent(entityId, Threat.class);
        CreatureMeleeAttack creatureMeleeAttack = entityData.getComponent(entityId, CreatureMeleeAttack.class);

        // Create a new versions of them
        creatureComponent = new CreatureComponent(creatureComponent);
        creatureExperience = new CreatureExperience(creatureExperience);
        health = new Health(health);
        gold = new Gold(gold);
        senses = new Senses(senses);
        threat = new Threat(threat);
        creatureMeleeAttack = new CreatureMeleeAttack(creatureMeleeAttack);

        // Set the new stats
        creatureExperience.level = level;
        creatureExperience.experience = experience;

        // Update stats
        setAttributesByLevel(creatureComponent, creatureExperience, health, gold, senses, threat, creatureMeleeAttack);

        // Set the new components to the entity
        entityData.setComponents(entityId, creatureComponent, creatureExperience, health, gold, senses, threat, creatureMeleeAttack);
    }

    private void setAttributesByLevel(CreatureComponent creatureComponent, CreatureExperience creatureExperience, Health healthComponent, Gold goldComponent, Senses sensesComponent, Threat threatComponent, CreatureMeleeAttack creatureMeleeAttack) {
        Creature creature = kwdFile.getCreature(creatureComponent.creatureId);
        Map<Variable.CreatureStats.StatType, Variable.CreatureStats> stats = kwdFile.getCreatureStats(creatureExperience.level);
        Creature.Attributes attributes = creature.getAttributes();
        creatureComponent.height = attributes.getHeight() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.HEIGHT_TILES).getValue() : 100) / 100);
        if (healthComponent != null) {
            int prevMaxHealth = healthComponent.maxHealth;
            healthComponent.maxHealth = attributes.getHp() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.HEALTH).getValue() : 100) / 100);
            healthComponent.health = healthComponent.maxHealth * healthComponent.health / prevMaxHealth;
            healthComponent.ownLandHealthIncrease = attributes.getOwnLandHealthIncrease() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.OWN_LAND_HEALTH_INCREASE_PER_SECOND).getValue() : 100) / 100);
        }
        creatureComponent.fear = attributes.getFear() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.FEAR).getValue() : 100) / 100);
        threatComponent.threat = attributes.getThreat() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.THREAT).getValue() : 100) / 100);
        creatureComponent.pay = attributes.getPay() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.PAY).getValue() : 100) / 100);
        goldComponent.maxGold = attributes.getMaxGoldHeld() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.MAX_GOLD_HELD).getValue() : 100) / 100);
        creatureComponent.hungerFill = attributes.getHungerFill() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.HUNGER_FILL_CHICKENS).getValue() : 100) / 100);
        creatureComponent.manaGenPrayer = attributes.getManaGenPrayer() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.MANA_GENERATED_BY_PRAYER_PER_SECOND).getValue() : 100) / 100);
        creatureExperience.experienceToNextLevel = attributes.getExpForNextLevel() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.EXPERIENCE_POINTS_FOR_NEXT_LEVEL).getValue() : 100) / 100);
        creatureExperience.experiencePerSecond = attributes.getExpPerSecond() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.EXPERIENCE_POINTS_PER_SECOND).getValue() : 100) / 100);
        creatureExperience.experiencePerSecondTraining = attributes.getExpPerSecondTraining() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.EXPERIENCE_POINTS_FROM_TRAINING_PER_SECOND).getValue() : 100) / 100);
        creatureComponent.researchPerSecond = attributes.getResearchPerSecond() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.RESEARCH_POINTS_PER_SECOND).getValue() : 100) / 100);
        creatureComponent.manufacturePerSecond = attributes.getManufacturePerSecond() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.MANUFACTURE_POINTS_PER_SECOND).getValue() : 100) / 100);
        creatureComponent.decomposeValue = attributes.getDecomposeValue() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.DECOMPOSE_VALUE).getValue() : 100) / 100);
        creatureComponent.speed = attributes.getSpeed() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.SPEED_TILES_PER_SECOND).getValue() : 100) / 100);
        creatureComponent.runSpeed = attributes.getRunSpeed() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.RUN_SPEED_TILES_PER_SECOND).getValue() : 100) / 100);
        creatureComponent.tortureTimeToConvert = attributes.getTortureTimeToConvert() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.TORTURE_TIME_TO_CONVERT_SECONDS).getValue() : 100) / 100);
        creatureComponent.posessionManaCost = attributes.getPossessionManaCost() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.POSSESSION_MANA_COST_PER_SECOND).getValue() : 100) / 100);
        if (sensesComponent != null) {
            sensesComponent.distanceCanHear = attributes.getDistanceCanHear() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.DISTANCE_CAN_HEAR_TILES).getValue() : 100) / 100);
        }
        goldComponent.gold = attributes.getInitialGoldHeld() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.INITIAL_GOLD_HELD).getValue() : 100) / 100);
        creatureMeleeAttack.damage = creature.getMeleeDamage() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.MELEE_DAMAGE).getValue() : 100) / 100);
        creatureMeleeAttack.rechargeTime = creature.getMeleeRecharge() * ((stats != null ? stats.get(Variable.CreatureStats.StatType.MELEE_RECHARGE_TIME_SECONDS).getValue() : 100) / 100);

        // FIXME: We should know when we run and when we walk and set the speed
        // Steering
        //setMaxLinearSpeed(speed);
    }

    @Override
    public void spawnHeroParty(short partyId, PartyType partyType, Vector2f position) {
        /**
         * In the game manual it is said that it is entirely possible to have
         * multiple parties with the same party ID, the old party just isn't
         * controlled anymore
         */
        IPartyController partyController = creaturePartiesByPartyId.get(partyId);
        if (partyController.isCreated()) {
            partyController = new PartyController(heroParties.get(partyId));
            LOGGER.log(Level.FINE, "Re-spawning party {0}!", partyId);
        }
        partyController.setType(partyType);
        partyController.create();
        for (Thing.GoodCreature creature : partyController.getMembers()) {
            EntityId entityId = spawnCreature(creature, position);
            entityData.setComponent(entityId, new Party(partyController.getId()));

            partyController.addMemberInstance(creature, createController(entityId));
        }
        creaturePartiesByPartyId.put(partyId, partyController);

        // TODO: Hmm, should we clean these up...
        creaturePartiesById.put(partyController.getId(), partyController);

        // TODO: listener, mainly for the PartyTrigger, to replace the party with new instance just in case
    }

    @Override
    public IPartyController getParty(short partyId) {
        return creaturePartiesByPartyId.get(partyId);
    }

    @Override
    public IPartyController getPartyById(long id) {
        return creaturePartiesById.get(id);
    }

    @Override
    public List<IPartyController> getParties() {
        return new ArrayList<>(creaturePartiesByPartyId.values());
    }

    @Override
    public ICreatureController createController(EntityId entityId) {
        CreatureComponent creatureComponent = entityData.getComponent(entityId, CreatureComponent.class);
        if (creatureComponent == null) {
            throw new RuntimeException("Entity " + entityId + " doesn't represent a creature!");
        }
        return creatureControllersByEntityId.computeIfAbsent(entityId, (id) -> {
            return new WeakReference<>(new CreatureController(id, entityData, kwdFile.getCreature(creatureComponent.creatureId), gameController.getNavigationService(), gameController.getTaskManager(), gameTimer, gameSettings, this, gameController.getEntityLookupService(), mapController, levelInfo));
        }).get();
    }

    @Override
    public boolean isValidEntity(EntityId entityId) {
        return entityData.getComponent(entityId, CreatureComponent.class) != null;
    }

}