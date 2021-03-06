package com.codenjoy.dojo.services;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2016 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.codenjoy.dojo.services.hero.HeroData;
import com.codenjoy.dojo.services.multiplayer.MultiplayerType;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiConsumer;

@Component
public class PlayerGames implements Iterable<PlayerGame>, Tickable {

    private static Logger logger = LoggerFactory.getLogger(PlayerGames.class);

    public static final int TICKS_FOR_REMOVE = 60*30; // 15 минут без игры - дисквалификация
    private List<PlayerGame> playerGames = new LinkedList<PlayerGame>();

    private BiConsumer<Player, Joystick> onAddPlayer;
    private BiConsumer<Player, Joystick> onRemovePlayer;

    public PlayerGames() {}
    public PlayerGames(Statistics statistics) { // TODO
        this.statistics = statistics;
    }

    private @Autowired Statistics statistics;

    public void remove(Player player) {
        int index = playerGames.indexOf(player);
        if (index == -1) return;
        playerGames.remove(index).remove();
    }

    public PlayerGame get(String playerName) {
        for (PlayerGame playerGame : playerGames) {
            if (playerGame.getPlayer().getName().equals(playerName)) {
                return playerGame;
            }
        }
        return NullPlayerGame.INSTANCE;
    }

    public PlayerGame add(Player player, Game game) {
        PlayerSpy spy = statistics.newPlayer(player);

        LazyJoystick joystick = new LazyJoystick(game, spy);
        if (onAddPlayer != null) {
            onAddPlayer.accept(player, joystick);
        }
        PlayerGame result = new PlayerGame(player, game, joystick, onRemovePlayer);
        playerGames.add(result);
        return result;
    }

    public boolean isEmpty() {
        return playerGames.isEmpty();
    }

    @Override
    public Iterator<PlayerGame> iterator() {
        return playerGames.iterator();
    }

    public List<Player> players() {
        List<Player> result = new ArrayList<Player>(playerGames.size());

        for (PlayerGame playerGame : playerGames) {
            result.add(playerGame.getPlayer());
        }

        return result;
    }

    public int size() {
        return playerGames.size();
    }

    public void clear() {
        for (Player player : players()) {
            remove(player);
        }
    }

    public List<PlayerGame> getAll(String gameType) {
        List<PlayerGame> result = new LinkedList<PlayerGame>();

        for (PlayerGame playerGame : playerGames) {
            if (playerGame.getPlayer().getGameName().equals(gameType)) {
                result.add(playerGame);
            }
        }

        return result;
    }

    public List<GameType> getGameTypes() {
        List<GameType> result = new LinkedList<GameType>();

        for (PlayerGame playerGame : playerGames) {
            GameType gameType = playerGame.getPlayer().getGameType();
            if (!result.contains(gameType)) {
                result.add(gameType);
            }
        }

        return result;
    }

    private void quietTick(Tickable tickable) {
        try {
            tickable.tick();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void tick() {
//        long time = System.currentTimeMillis();

        for (PlayerGame playerGame : playerGames) {
            quietTick(playerGame);
        }

        quietTick(statistics);

//        removeNotActivePlayers();

        for (final PlayerGame playerGame : playerGames) {
            final Game game = playerGame.getGame();
            if (game.isGameOver()) {
                quietTick(() -> game.newGame());
            }
        }

        List<GameType> gameTypes = getGameTypes();  // TODO потестить еще отдельно
        for (GameType gameType : gameTypes) {
            List<PlayerGame> games = getAll(gameType.name());
            if (gameType.getMultiplayerType() == MultiplayerType.MULTIPLE) {
                if (!games.isEmpty()) {
                    quietTick(games.iterator().next().getGame());
                }
            } else {
                for (PlayerGame playerGame : games) {
                    quietTick(playerGame.getGame());
                }
            }
        }

        getGameTypes().forEach(gameType -> gameType.tick());

//        if (logger.isDebugEnabled()) {
//            time = System.currentTimeMillis() - time;
//            logger.debug("PlayerGames.tick() is {} ms", time);
//        }
    }

    private void removeNotActivePlayers() {
        for (Player player : statistics.getPlayers(Statistics.WAIT_TICKS_MORE_OR_EQUALS, TICKS_FOR_REMOVE)) {
            remove(player);
        }
    }

    public Map<String, GameData> getGamesDataMap() {
        Map<String, GameData> result = new LinkedHashMap<>();
        for (GameType gameType : getGameTypes()) {
            int boardSize = gameType.getBoardSize().getValue();
            GuiPlotColorDecoder decoder = new GuiPlotColorDecoder(gameType.getPlots());
            JSONObject scores = getScoresJSON(gameType.name());
            JSONObject heroesData = getCoordinatesJSON(gameType.name());

            result.put(gameType.name(), new GameData(boardSize, decoder, scores, heroesData));
        }
        return result;
    }

    private JSONObject getCoordinatesJSON(String gameType) {
        List<PlayerGame> playerGames = getAll(gameType);

        Map<Player, List<Player>> playersMap = new HashMap<>();
        for (PlayerGame playerGame : playerGames) {
            Player player = playerGame.getPlayer();
            Game game = playerGame.getGame();
            HeroData heroData = game.getHero();
            List<Game> gamesGroup = heroData.playersGroup();
            List<Player> playersGroup = new LinkedList<>();
            if (gamesGroup == null) {
                playersGroup.add(player);
            } else {
                for (Game game2 : gamesGroup) {
                    int index = playerGames.indexOf(PlayerGame.by(game2));
                    if (index != -1) {
                        playersGroup.add(playerGames.get(index).getPlayer());
                    } else {
                        // TODO этого не должн случиться, но лучше порефакторить
                    }
                }
            }
            playersMap.put(player, playersGroup);
        }

        Map<String, JSONObject> heroesData = new HashMap<>();
        for (PlayerGame playerGame : playerGames) {
            heroesData.put(playerGame.getPlayer().getName(),
                    new JSONObject(playerGame.getGame().getHero()));
        }

        JSONObject result = new JSONObject();
        for (Map.Entry<Player, List<Player>> entry : playersMap.entrySet()) {
            Player player1 = entry.getKey();

            JSONObject map = new JSONObject();
            result.put(player1.getName(), map);

            for (Player player2 : entry.getValue()) {
                String name = player2.getName();
                map.put(name, heroesData.get(name));
            }
        }
        return result;
    }

    private JSONObject getScoresJSON(String gameType) {
        JSONObject result = new JSONObject();
        for (PlayerGame playerGame : getAll(gameType)) {
            Player player = playerGame.getPlayer();
            result.put(player.getName(), player.getScore());
        }
        return result;
    }

    public void onAddPlayer(BiConsumer<Player, Joystick> consumer) {
        this.onAddPlayer = consumer;
    }

    public void onRemovePlayer(BiConsumer<Player, Joystick> consumer) {
        this.onRemovePlayer = consumer;
    }
}
